//
// NodeRendererJ3D.java
//

/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 2001 Bill Hibbard, Curtis Rueden, Tom
Rink, Dave Glowacki, Steve Emmerson, Tom Whittaker, Don Murray, and
Tommy Jasmin.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.

You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
MA 02111-1307, USA
*/

package visad.cluster;

import visad.*;
import visad.util.Delay;
import visad.collab.*;
import visad.java3d.*;
import visad.java2d.*;

import javax.media.j3d.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import java.rmi.*;
import java.io.Serializable;

/**
   NodeRendererJ3D is the VisAD class for transforming
   data into VisADSceneGraphObjects, but not rendering,
   on cluster nodes
*/
public class NodeRendererJ3D extends DefaultRendererJ3D {

  private NodeAgent agent = null;

  private boolean enable_transform = false;

  /** this constructor is need for NodeDisplayRendererJ3D.makeDefaultRenderer()
      but it should never be called */
  public NodeRendererJ3D () {
    this(null);
  }

  /** this DataRenderer transforms data into VisADSceneGraphObjects,
      but does not render, on cluster nodes;
      send scene graphs back via NodeAgent */
  public NodeRendererJ3D (NodeAgent a) {
    super();
    agent = a;
  }

  public ShadowType makeShadowFunctionType(
         FunctionType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowNodeFunctionTypeJ3D(type, link, parent);
  }

  public ShadowType makeShadowRealTupleType(
         RealTupleType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowNodeRealTupleTypeJ3D(type, link, parent);
  }

  public ShadowType makeShadowRealType(
         RealType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowNodeRealTypeJ3D(type, link, parent);
  }

  public ShadowType makeShadowSetType(
         SetType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowNodeSetTypeJ3D(type, link, parent);
  }

  public ShadowType makeShadowTupleType(
         TupleType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowNodeTupleTypeJ3D(type, link, parent);
  }

  public void enableTransform() {
    enable_transform = true;
  }

  public DataShadow prepareAction(boolean go, boolean initialize,
                                  DataShadow shadow)
         throws VisADException, RemoteException {
    // don't autoscale: initialize = false
    return super.prepareAction(go, false, shadow);
  }

  /** create a VisADGroup scene graph for Data in links[0] */
  public BranchGroup doTransform() throws VisADException, RemoteException {

/*
should wait until all collab events are quiet
especially ScalarMapEvent.AUTO_SCALE
*/


/* WLH 16 April 2001
DisplaySyncImpl ds = (DisplaySyncImpl) getDisplay().getDisplaySync();
while (ds.isThreadRunning()) {
  System.out.println("wait for DisplaySync");
  new Delay(10);
}
*/


    // RendererJ3D.doAction is expecting a BranchGroup
    // so fake it
    BranchGroup fake_branch = new BranchGroup();
    fake_branch.setCapability(BranchGroup.ALLOW_DETACH);
    fake_branch.setCapability(Group.ALLOW_CHILDREN_READ);
    fake_branch.setCapability(Group.ALLOW_CHILDREN_WRITE);
    fake_branch.setCapability(Group.ALLOW_CHILDREN_EXTEND);

// System.out.println("NodeRendererJ3D.doTransform enabled = " + enable_transform);

    // don't do work unless requested by the client
    if (!enable_transform) return fake_branch;
    enable_transform = false;

/*
Vector map_vector = getDisplay().getMapVector();
Enumeration maps = map_vector.elements();
while (maps.hasMoreElements()) {
  ScalarMap map = (ScalarMap) maps.nextElement();
  double[] range = map.getRange();
  Control control = map.getControl();
  System.out.println(map + " " + ((float) range[0]) + " " + ((float) range[1]));
  System.out.println("  " + control);
}
*/

    VisADGroup branch = new VisADGroup();

    DataDisplayLink[] Links = getLinks();
    if (Links == null || Links.length == 0) {
      return null;
    }
    DataDisplayLink link = Links[0];

    ShadowTypeJ3D type = (ShadowTypeJ3D) link.getShadow();

    // initialize valueArray to missing
    int valueArrayLength = getDisplay().getValueArrayLength();
    float[] valueArray = new float[valueArrayLength];
    for (int i=0; i<valueArrayLength; i++) {
      valueArray[i] = Float.NaN;
    }

    Data data;
    try {
      data = link.getData();
    } catch (RemoteException re) {
      if (visad.collab.CollabUtil.isDisconnectException(re)) {
        getDisplay().connectionFailed(this, link);
        removeLink(link);
        return null;
      }
      throw re;
    }

    if (data == null) {
      branch = null;
      addException(
        new DisplayException("Data is null: NodeRendererJ3D.doTransform"));
    }
    else {
      link.start_time = System.currentTimeMillis();
      link.time_flag = false;
      type.preProcess();

      boolean post_process;
      try {
        // transform data into a depiction under branch
        post_process = type.doTransform(branch, data, valueArray,
                                        link.getDefaultValues(), this);
      } catch (RemoteException re) {
        if (visad.collab.CollabUtil.isDisconnectException(re)) {
          getDisplay().connectionFailed(this, link);
          removeLink(link);
          return null;
        }
        throw re;
      }

      if (post_process) type.postProcess(branch);
    }
    link.clearData();

    // send VisADGroup scene graph in branch back to client
    if (agent != null) agent.sendToClient(branch);

    // RendererJ3D.doAction is expecting a BranchGroup
    // so fake it
    return fake_branch;
  }

  public static void main(String args[])
         throws VisADException, RemoteException {

    DisplayImpl display =
      new DisplayImplJ3D("display", new NodeDisplayRendererJ3D(),
                         DisplayImplJ3D.TRANSFORM_ONLY);

    // create JFrame (i.e., a window) for display and slider
    JFrame frame = new JFrame("test NodeRendererJ3D");
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {System.exit(0);}
    });

    // create JPanel in JFrame
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
    panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
    frame.getContentPane().add(panel);

    // add display to JPanel
    // panel.add(display.getComponent());

    // set size of JFrame and make it visible
    frame.setSize(500, 500);
    frame.setVisible(true);
  }

}

