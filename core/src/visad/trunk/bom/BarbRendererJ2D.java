
//
// BarbRendererJ2D.java
//

/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 1998 Bill Hibbard, Curtis Rueden, Tom
Rink and Dave Glowacki.
 
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 1, or (at your option)
any later version.
 
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License in file NOTICE for more details.
 
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package visad.bom;
 
import visad.*;
import visad.java2d.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import java.rmi.*;


/**
   BarbRendererJ2D is the VisAD class for rendering of
   wind barbs under Java2D - otherwise it behaves just
   like DefaultRendererJ2D
*/
public class BarbRendererJ2D extends DefaultRendererJ2D {

  /** this DataRenderer supports direct manipulation for RealTuple
      representations of wind barbs; four of the RealTuple's Real
      components must be mapped to XAxis, YAxis, Flow1X and Flow1Y */
  public BarbRendererJ2D () {
    super();
  }
 
  public ShadowType makeShadowFunctionType(
         FunctionType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowBarbFunctionTypeJ2D(type, link, parent);
  }

  public ShadowType makeShadowRealTupleType(
         RealTupleType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowBarbRealTupleTypeJ2D(type, link, parent);
  }

  public ShadowType makeShadowRealType(
         RealType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowBarbRealTypeJ2D(type, link, parent);
  }

  public ShadowType makeShadowSetType(
         SetType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowBarbSetTypeJ2D(type, link, parent);
  }

  public ShadowType makeShadowTupleType(
         TupleType type, DataDisplayLink link, ShadowType parent)
         throws VisADException, RemoteException {
    return new ShadowBarbTupleTypeJ2D(type, link, parent);
  }

  static final int N = 5;

  /** test BarbRendererJ2D */
  public static void main(String args[])
         throws VisADException, RemoteException {
    RealType x = new RealType("x");
    RealType y = new RealType("y");
    RealType flowx = new RealType("flowx",
                          CommonUnit.meterPerSecond, null);
    RealType flowy = new RealType("flowy",
                          CommonUnit.meterPerSecond, null);
    RealType red = new RealType("red");
    RealType green = new RealType("green");
    RealType index = new RealType("index");
/*
    RealTupleType flowxy = new RealTupleType(flowx, flowy);
    RealType flow_degree = new RealType("flow_degree",
                          CommonUnit.degree, null);
    RealType flow_speed = new RealType("flow_speed",
                          CommonUnit.meterPerSecond, null);
    RealTupleType flowds =
      new RealTupleType(new RealType[] {flow_degree, flow_speed},
      new WindPolarCoordinateSystem(flowxy), null);
    TupleType range = new TupleType(new MathType[]
          {x, y, flowds, red, green});
    FunctionType flow_field = new FunctionType(index, range);
*/
    RealTupleType range = new RealTupleType(new RealType[]
          {x, y, flowx, flowy, red, green});
    FunctionType flow_field = new FunctionType(index, range);

    DisplayImpl display = new DisplayImplJ2D("display1");
    ScalarMap xmap = new ScalarMap(x, Display.XAxis);
    display.addMap(xmap);
    xmap.setRange(-1.0, 1.0);
    ScalarMap ymap = new ScalarMap(y, Display.YAxis);
    display.addMap(ymap);
    ymap.setRange(-1.0, 1.0);
    ScalarMap flowx_map = new ScalarMap(flowx, Display.Flow1X);
    display.addMap(flowx_map);
    flowx_map.setRange(-1.0, 1.0);
    ScalarMap flowy_map = new ScalarMap(flowy, Display.Flow1Y);
    display.addMap(flowy_map);
    flowy_map.setRange(-1.0, 1.0);
    FlowControl flow_control = (FlowControl) flowy_map.getControl();
    flow_control.setFlowScale(0.1f);
    display.addMap(new ScalarMap(red, Display.Red));
    display.addMap(new ScalarMap(green, Display.Green));
    display.addMap(new ConstantMap(1.0, Display.Blue));

    Integer1DSet set = new Integer1DSet(N * N);
    double[][] values = new double[6][N * N];
    int m = 0;
    for (int i=0; i<N; i++) {
      for (int j=0; j<N; j++) {
        double u = 2.0 * i / (N - 1.0) - 1.0;
        double v = 2.0 * j / (N - 1.0) - 1.0;
        values[0][m] = u;
        values[1][m] = v;
        double fx = 30.0 * u;
        double fy = 30.0 * v;
/*
        values[2][m] = Math.sqrt(fx * fx + fy * fy);
        values[3][m] =
          Data.RADIANS_TO_DEGREES * Math.atan2(fx, fy);
*/
        values[2][m] = fx;
        values[3][m] = fy;
        values[4][m] = u;
        values[5][m] = v;
        m++;
      }
    }
    FlatField field = new FlatField(flow_field, set);
    field.setSamples(values);
    DataReferenceImpl ref = new DataReferenceImpl("ref");
    ref.setData(field);
    display.addReferences(new BarbRendererJ2D(), ref);

    // create JFrame (i.e., a window) for display and slider
    JFrame frame = new JFrame("test BarbRendererJ2D");
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
    panel.add(display.getComponent());
 
    // set size of JFrame and make it visible
    frame.setSize(500, 500);
    frame.setVisible(true);
  }

}

