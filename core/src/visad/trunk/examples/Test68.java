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

import java.awt.Container;
import java.awt.event.*;
import java.io.IOException;
import java.rmi.RemoteException;

import javax.swing.*;

import visad.*;
import visad.java2d.DisplayImplJ2D;
import visad.java3d.DisplayImplJ3D;
import visad.util.GMCWidget;

public class Test68
  extends UISkeleton
{
  private int port = 0;

  private boolean twoD = false;

  boolean hasClientServerMode() { return false; }

  public Test68() { }

  public Test68(String[] args)
    throws RemoteException, VisADException
  {
    super(args);
  }

  int checkExtraOption(String progName, char ch, String arg)
  {
    if (ch != '2') {
      return 0;
    }

    twoD = true;
    return 1;
  }

  int checkExtraKeyword(String testName, int argc, String[] args)
  {
    String arg = args[argc];
    int d = 0;
    try {
      d = Integer.parseInt(arg);
    }
    catch (NumberFormatException exc) { }
    if (d < 1 || d > 9999) {
      System.err.println(testName + ": Ignoring parameter \"" + arg +
        "\": port must be between 1 and 9999");
    } else {
      port = d;
    }

    return 1;
  }

  DisplayImpl[] setupServerDisplays()
    throws RemoteException, VisADException
  {
    DisplayImpl[] dpys = new DisplayImpl[1];
    if (twoD) {
      dpys[0] = new DisplayImplJ2D("display");
    } else {
      dpys[0] = new DisplayImplJ3D("display");
    }
    return dpys;
  }

  GraphicsModeControl gmc;

  void setupServerData(LocalDisplay[] dpys)
    throws RemoteException, VisADException
  {
    RealType ir_radiance = new RealType("ir_radiance", null, null);
    int size = 64;
    DisplayImpl display1 = (DisplayImpl) dpys[0];

    if (twoD) {
      RealType count = new RealType("count", null, null);
      FunctionType ir_histogram = new FunctionType(ir_radiance, count);
      FlatField histogram1 = FlatField.makeField(ir_histogram, size, false);

      System.out.print("Creating Java2D display...");
      display1.addMap(new ScalarMap(count, Display.YAxis));
      display1.addMap(new ScalarMap(ir_radiance, Display.XAxis));
      display1.addMap(new ConstantMap(0.0, Display.Red));
      display1.addMap(new ConstantMap(1.0, Display.Green));
      display1.addMap(new ConstantMap(0.0, Display.Blue));

      gmc = display1.getGraphicsModeControl();

      DataReferenceImpl ref_histogram1;
      ref_histogram1 = new DataReferenceImpl("ref_histogram1");
      ref_histogram1.setData(histogram1);
      display1.addReference(ref_histogram1, null);
    } else {
      RealType vis_radiance = new RealType("vis_radiance", null, null);
      RealType[] types = {RealType.Latitude, RealType.Longitude};
      RealType[] types2 = {vis_radiance, ir_radiance};
      RealTupleType earth_location = new RealTupleType(types);
      RealTupleType radiance = new RealTupleType(types2);
      FunctionType image_tuple = new FunctionType(earth_location, radiance);
      FlatField imaget1 = FlatField.makeField(image_tuple, size, false);

      System.out.print("Creating Java3D display...");
      display1.addMap(new ScalarMap(RealType.Latitude, Display.YAxis));
      display1.addMap(new ScalarMap(RealType.Longitude, Display.XAxis));
      display1.addMap(new ScalarMap(vis_radiance, Display.ZAxis));
      display1.addMap(new ScalarMap(vis_radiance, Display.Green));
      display1.addMap(new ScalarMap(vis_radiance, Display.IsoContour));
      display1.addMap(new ConstantMap(0.5, Display.Blue));
      display1.addMap(new ConstantMap(0.5, Display.Red));

      gmc = display1.getGraphicsModeControl();
      gmc.setPointSize(2.0f);
      gmc.setPointMode(false);
      gmc.setMissingTransparent(true);

      DataReferenceImpl ref_imaget1 = new DataReferenceImpl("ref_imaget1");
      ref_imaget1.setData(imaget1);
      display1.addReference(ref_imaget1, null);
    }

    // create the SocketSlaveDisplay for automatic handling of socket clients
    SocketSlaveDisplay serv = null;
    try {
      if (port > 0) {
        serv = new SocketSlaveDisplay(display1, port);
      } else {
        serv = new SocketSlaveDisplay(display1);
      }
    }
    catch (IOException exc) {
      System.err.println("Unable to create the SocketSlaveDisplay:");
      exc.printStackTrace();
    }
    if (serv != null) {
      System.out.println("SocketSlaveDisplay created.\n" +
        "To connect a client from within a web browser,\n" +
        "use the VisADApplet applet found in visad/browser.\n" +
        "Note that an applet cannot communicate with a server\n" +
        "via the network unless both applet and server\n" +
        "originate from the same machine.  In the future,\n" +
        "VisAD's SocketSlaveDisplay will support communication\n" +
        "through a proxy server.");
    }

    // set up widget frame
    JFrame widgetFrame = new JFrame("Controls");
    widgetFrame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    JPanel pane = new JPanel();
    Container widgets = display1.getWidgetPanel();
    widgetFrame.setContentPane(widgets);
    widgetFrame.pack();
    widgetFrame.show();
  }

  String getFrameTitle() { return "SocketSlaveDisplay server"; }

  public String toString() { return " [-2d] port: SocketSlaveDisplay"; }

  public static void main(String[] args)
    throws RemoteException, VisADException
  {
    new Test68(args);
  }
}
