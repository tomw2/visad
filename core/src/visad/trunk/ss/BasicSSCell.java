
//
// BasicSSCell.java
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

package visad.ss;

import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.rmi.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import visad.*;
import visad.data.*;
import visad.data.netcdf.Plain;
import visad.data.visad.VisADForm;
import visad.formula.*;
import visad.java2d.*;
import visad.java3d.*;

/** BasicSSCell represents a single spreadsheet display cell.  BasicSSCells
    can be added to a VisAD user interface to provide some of the capabilities
    presented in the VisAD SpreadSheet program.  Other capabilities, like the
    file loader and data mapping dialog boxes, are available only with a
    FancySSCell.<P> */
public class BasicSSCell extends JPanel {

  /** list of SSCells on this JVM */
  static Vector SSCellVector = new Vector();

  /** counter for the number of cells currently saving data */
  static int Saving = 0;

  /** name of this BasicSSCell */
  String Name;

  /** URL from where data was imported, if any */
  URL Filename = null;

  /** RMI address from where data was imported, if any */
  String RMIAddress = null;

  /** formula of this BasicSSCell, if any */
  String Formula = "";

  /** whether this display is remote */
  boolean IsRemote;

  /** BasicSSCell's associated VisAD DisplayPanel */
  JPanel VDPanel;

  /** BasicSSCell's associated VisAD Display */
  DisplayImpl VDisplay;

  /** BasicSSCell's associated VisAD RemoteDisplay */
  RemoteDisplay RemoteVDisplay = null;

  /** BasicSSCell's associated VisAD DataReference */
  DataReferenceImpl DataRef;

  /** BasicSSCell's associated VisAD RemoteDataReference */
  RemoteDataReferenceImpl RemoteDataRef;

  /** constant for use with Dimension2D variable */
  static final int JAVA3D_3D = 1;

  /** constant for use with Dimension2D variable */
  static final int JAVA2D_2D = 2;

  /** constant for use with Dimension2D variable */
  static final int JAVA3D_2D = 3;

  /** FormulaManager object used by all BasicSSCells with formulas */
  static final FormulaManager fm = FormulaUtil.createStandardManager();

  /** this BasicSSCell's DisplayListener */
  DisplayListener DListen = null;

  /** whether the DisplayPanel is 2-D or 3-D, Java2D or Java3D */
  int Dimension2D = -1;

  /** whether the BasicSSCell has mappings from Data to Display */
  boolean HasMappings = false;

  /** prevents simultaneous GUI manipulation */
  private Object Lock = new Object();

  /** construct a new BasicSSCell with the given name */
  public BasicSSCell(String name) throws VisADException, RemoteException {
    this(name, (RemoteServer) null);
  }

  /** construct a new BasicSSCell with the given name, that gets its
      information from the given RemoteServer. The associated SSCell on the
      server end must have already invoked its addToRemoteServer method */
  public BasicSSCell(String name, RemoteServer rs) throws VisADException,
                                                          RemoteException {
    if (name == null) {
      throw new VisADException("BasicSSCell: name cannot be null");
    }
    Enumeration panels = SSCellVector.elements();
    while (panels.hasMoreElements()) {
      BasicSSCell panel = (BasicSSCell) panels.nextElement();
      if (name.equalsIgnoreCase(panel.Name)) {
        throw new VisADException("BasicSSCell: name already used");
      }
    }
    Name = name;
    SSCellVector.add(this);

    if (rs != null) RemoteVDisplay = rs.getDisplay(name);
    IsRemote = (RemoteVDisplay != null);

    CellImpl ucell = new CellImpl() {
      public void doAction() {
        // redisplay this cell's data when it changes
        Data value = null;
        try {
          value = (Data) fm.getThing(Name);
        }
        catch (ClassCastException exc) {
          setError("Final value is not of the correct type.");
        }
        catch (FormulaException exc) {
          setError("The formula could not be evaluated.");
        }

        if (value == null) {
          // no value; clear display
          try {
            clearDisplay();
          }
          catch (VisADException exc) {
            setError("Unable to clear old data.");
          }
          catch (RemoteException exc) {
            setError("Unable to clear old data.");
          }
        }
        else {
          // update cell's data
          setVDPanel(true);
        }
        String[] es = fm.getErrors(Name);
        if (es != null) setErrors(es);
        notifyListeners(SSCellChangeEvent.DATA_CHANGE);
      }
    };
    DataRef = new DataReferenceImpl(name);
    RemoteDataRef = new RemoteDataReferenceImpl(DataRef);
    fm.createVar(Name, DataRef);
    ucell.addReference(DataRef);
    setDimension(JAVA2D_2D);
    VDPanel = (JPanel) VDisplay.getComponent();
    setPreferredSize(new Dimension(0, 0));
    setBackground(Color.black);
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
  }

  /** get this SSCell's name */
  public String getName() {
    return Name;
  }

  /** get this SSCell's VisAD Display */
  public DisplayImpl getDisplay() {
    return VDisplay;
  }

  /** get this SSCell's VisAD RemoteDisplay */
  public RemoteDisplay getRemoteDisplay() {
    return RemoteVDisplay;
  }

  /** add or remove VDPanel from this BasicSSCell */
  void setVDPanel(boolean value) {
    synchronized (Lock) {
      removeAll();
      if (value) add(VDPanel);
      validate();
      repaint();
    }
  }

  /** display an error in this BasicSSCell, or setError(null) for no error */
  void setError(String msg) {
    String[] s = (msg == null ? null : new String[] {msg});
    setErrors(s);
  }

  /** display errors in this BasicSSCell, or setErrors(null) for no errors */
  void setErrors(String[] msg) {
    JComponent ErrorCanvas;
    if (msg == null) ErrorCanvas = null;
    else {
      final String[] m = msg;
      ErrorCanvas = new JComponent() {
        public void paint(Graphics g) {
          g.setColor(Color.white);
          String s = (m.length == 1 ? "An error" : "Errors") +
                     " occurred while computing this cell:";
          g.drawString(s, 8, 20);
          for (int i=0; i<m.length; i++) g.drawString(m[i], 8, 15*i + 50);
        }
      };
    }
    synchronized (Lock) {
      removeAll();
      if (ErrorCanvas != null) {
        add(ErrorCanvas);
      }
      validate();
      repaint();
    }
  }

  /** add this SSCell to the given RemoteServer. SSCell servers must call this
      method for each cell before clients can clone the cells with the
      BasicSSCell(String name, RemoteServer rs) constructor, and before
      the cells can be exported as RMI addresses */
  public void addToRemoteServer(RemoteServerImpl rs) throws RemoteException {
    if (IsRemote) {
      throw new RemoteException("This cell is a clone of another cell!");
    }
    else {
      rs.addDataReference(RemoteDataRef);
      rs.addDisplay((RemoteDisplayImpl) RemoteVDisplay);
    }
  }

  /** list of SSCellListeners to be notified of changes */
  private Vector list = new Vector();

  /** add an SSCellListener to be notified of changes */
  public void addSSCellChangeListener(SSCellListener l) {
    if (!list.contains(l)) list.add(l);
  }

  /** remove an SSCellListener */
  public void removeListener(SSCellListener l) {
    if (list.contains(l)) list.remove(l);
  }

  /** remove all SSCellListeners */
  public void removeAllListeners() {
    list.removeAllElements();
  }

  /** notify SSCellListeners that change occurred */
  private void notifyListeners(int changeType) {
    SSCellChangeEvent e = new SSCellChangeEvent(this, changeType);
    for (int i=0; i<list.size(); i++) {
      SSCellListener l = (SSCellListener) list.elementAt(i);
      l.ssCellChanged(e);
    }
  }

  /** construct a BasicSSCell with the given name and data string */
  public BasicSSCell(String name, String info) throws VisADException,
                                                      RemoteException {
    this(name);
    if (info != null) setSSCellString(info);
  }

  /** return the BasicSSCell object with the specified display */
  public static BasicSSCell getSSCellByDisplay(Display d) {
    Enumeration panels = SSCellVector.elements();
    while (panels.hasMoreElements()) {
      BasicSSCell panel = (BasicSSCell) panels.nextElement();
      if (d == (Display) panel.VDisplay) return panel;
    }
    return null;
  }

  /** return the BasicSSCell object with the specified name */
  public static BasicSSCell getSSCellByName(String name) {
    Enumeration panels = SSCellVector.elements();
    while (panels.hasMoreElements()) {
      BasicSSCell panel = (BasicSSCell) panels.nextElement();
      if (name.equalsIgnoreCase(panel.Name)) return panel;
    }
    return null;
  }

  /** obtain a Vector consisting of all ScalarTypes present in data's MathType;
      return the number of duplicate ScalarTypes found */
  static int getRealTypes(Data data, Vector v) {
    MathType dataType;
    try {
      dataType = data.getType();
    }
    catch (RemoteException exc) {
      return -1;
    }
    catch (VisADException exc) {
      return -1;
    }
    int[] i = new int[1];
    i[0] = 0;

    if (dataType instanceof FunctionType) {
      parseFunction((FunctionType) dataType, v, i);
    }
    else if (dataType instanceof SetType) {
      parseSet((SetType) dataType, v, i);
    }
    else if (dataType instanceof TupleType) {
      parseTuple((TupleType) dataType, v, i);
    }
    else parseScalar((ScalarType) dataType, v, i);

    return i[0];
  }

  /** used by getRealTypes */
  private static void parseFunction(FunctionType mathType, Vector v, int[] i) {
    // extract domain
    RealTupleType domain = mathType.getDomain();
    parseTuple((TupleType) domain, v, i);

    // extract range
    MathType range = mathType.getRange();
    if (range instanceof FunctionType) {
      parseFunction((FunctionType) range, v, i);
    }
    else if (range instanceof SetType) {
      parseSet((SetType) range, v, i);
    }
    else if (range instanceof TupleType) {
      parseTuple((TupleType) range, v, i);
    }
    else parseScalar((ScalarType) range, v, i);

    return;
  }

  /** used by getRealTypes */
  private static void parseSet(SetType mathType, Vector v, int[] i) {
    // extract domain
    RealTupleType domain = mathType.getDomain();
    parseTuple((TupleType) domain, v, i);

    return;
  }

  /** used by getRealTypes */
  private static void parseTuple(TupleType mathType, Vector v, int[] i) {
    // extract components
    for (int j=0; j<mathType.getDimension(); j++) {
      MathType cType = null;
      try {
        cType = mathType.getComponent(j);
      }
      catch (VisADException exc) { }

      if (cType != null) {
        if (cType instanceof FunctionType) {
          parseFunction((FunctionType) cType, v, i);
        }
        else if (cType instanceof SetType) {
          parseSet((SetType) cType, v, i);
        }
        else if (cType instanceof TupleType) {
          parseTuple((TupleType) cType, v, i);
        }
        else parseScalar((ScalarType) cType, v, i);
      }
    }
    return;
  }

  /** used by getRealTypes */
  private static void parseScalar(ScalarType mathType, Vector v, int[] i) {
    if (mathType instanceof RealType) {
      if (v.contains(mathType)) i[0]++;
      v.add(mathType);
    }
  }

  /** return true if any BasicSSCell is currently saving data */
  public static boolean isSaving() {
    return Saving > 0;
  }

  /** reconstruct this BasicSSCell using the specified info string */
  public void setSSCellString(String info) throws VisADException,
                                                  RemoteException {
    // extract filename from info string
    if (!info.substring(0, 11).equals("filename = ")) {
      throw new VisADException("Invalid info string!");
    }
    int i=10;
    char c = '*';
    while (c != '\n') c = info.charAt(++i);
    String filename = info.substring(11, i++);
    if (filename.equals("null")) filename = null;

    // extract RMI address from info string
    if (!info.substring(i, i+6).equals("rmi = ")) {
      throw new VisADException("Invalid info string!");
    }
    i += 5;
    int oi = i + 1;
    c = '*';
    while (c != '\n') c = info.charAt(++i);
    String rmi = info.substring(oi, i++);
    if (rmi.equals("null")) rmi = null;

    // extract formula from info string
    if (!info.substring(i, i+10).equals("formula = ")) {
      throw new VisADException("Invalid info string!");
    }
    i += 9;
    oi = i + 1;
    c = '*';
    while (c != '\n') c = info.charAt(++i);
    String formula = info.substring(oi, i++);

    // extract dimension from info string
    if (!info.substring(i, i+6).equals("dim = ")) {
      throw new VisADException("Invalid info string!");
    }
    i += 5;
    oi = i + 1;
    c = '*';
    while (c != '\n') c = info.charAt(++i);
    String b = info.substring(oi, i++);
    int dim = -1;
    try {
      dim = Integer.parseInt(b);
    }
    catch (NumberFormatException exc) { }
    if (dim != JAVA3D_3D && dim != JAVA2D_2D && dim != JAVA3D_2D) {
      throw new VisADException("Invalid info string!");
    }

    // extract mappings from info string
    if (!info.substring(i, i+7).equals("maps = ")) {
      throw new VisADException("Invalid info string!");
    }
    Vector dnames = new Vector();
    Vector rnames = new Vector();
    i += 6;
    c = '*';
    while (c != '\n') {
      c = '*';
      oi = i + 1;
      while (c != ' ' && c != '\n') c = info.charAt(++i);
      if (c != '\n') {
        String dname = info.substring(oi, i++);
        dnames.add(dname);
        c = '*';
        oi = i;
        while (c != ' ' && c != '\n') c = info.charAt(++i);
        try {
          String s = (String) info.substring(oi, i);
          int q = Integer.parseInt(s);
          rnames.add(new Integer(q));
        }
        catch (NumberFormatException exc) {
          throw new VisADException("Invalid info string!");
        }
      }
    }

    // clear old stuff from cell
    clearCell();

    // set up dimension
    setDimension(dim);

    // set up filename
    if (filename != null) {
      URL u = null;
      try {
        u = new URL(filename);
      }
      catch (MalformedURLException exc) {
        throw new VisADException(exc.toString());
      }
      try {
        loadData(u);
      }
      catch (IOException exc) {
        throw new VisADException(exc.toString());
      }
    }

    // set up RMI address
    if (rmi != null) {
      try {
        loadRMI(rmi);
      }
      catch (MalformedURLException exc) {
        throw new VisADException(exc.toString());
      }
      catch (NotBoundException exc) {
        throw new VisADException(exc.toString());
      }
      catch (AccessException exc) {
        throw new VisADException(exc.toString());
      }
    }

    // set up formula
    if (!formula.equals("")) setFormula(formula);

    // set up mappings
    int len = dnames.size();
    if (len > 0) {
      ScalarMap[] maps = new ScalarMap[len];
      for (int j=0; j<len; j++) {
        ScalarType domain = ScalarType.getScalarTypeByName(
                            (String) dnames.elementAt(j));
        int q = ((Integer) rnames.elementAt(j)).intValue();
        DisplayRealType range = Display.DisplayRealArray[q];
        maps[j] = new ScalarMap(domain, range);
      }
      setMaps(maps);
    }
  }

  /** return the data string necessary to reconstruct this cell */
  public String getSSCellString() {
    String s = "filename = " + (Filename == null ?
                               "null" : Filename.toString()) + "\n";
    s = s + "rmi = " + RMIAddress + "\n";
    s = s + "formula = " + Formula + "\n";
    s = s + "dim = " + Dimension2D + "\n";
    s = s + "maps = ";
    ScalarMap[] maps = null;
    if (VDisplay != null) {
      Vector mapVector = VDisplay.getMapVector();
      int mvs = mapVector.size();
      if (mvs > 0) {
        for (int i=0; i<mvs; i++) {
          ScalarMap m = (ScalarMap) mapVector.elementAt(i);
          ScalarType domain = m.getScalar();
          DisplayRealType range = m.getDisplayScalar();
          int q = -1;
          for (int j=0; j<Display.DisplayRealArray.length; j++) {
            if (range.equals(Display.DisplayRealArray[j])) q = j;
          }
          if (i > 0) s = s + " ";
          s = s + domain.getName() + " " + q;
        }
        s = s + "\n";
      }
      else s = s + "null\n";
    }
    else s = s + "null\n";
    return s;
  }

  /** set up the DisplayListener for this cell */
  public void setDisplayListener(DisplayListener d) {
    if (DListen != null) VDisplay.removeDisplayListener(DListen);
    DListen = d;
    if (DListen != null) VDisplay.addDisplayListener(DListen);
  }

  /** map RealTypes to the display according to the specified ScalarMaps */
  public void setMaps(ScalarMap[] maps) throws VisADException,
                                               RemoteException {
    if (maps == null) return;
    clearMaps();
    /* CTR: TEMP */ if (Name.equals("A1")) (new VisADException("setMaps")).printStackTrace();
    /* CTR: START HERE: this exception is still being thrown.. I think it should be, but not
    from the autoDetect routine... because the autoDetect should be being disabled!  Try
    printing the status of AutoDetect whenever setAutoDetect is called to see if it's not
    lining up right (stupid thread crap)..... the goal is to fix the openFile routine in
    the SpreadSheet so that it works again, and loads the correct mappings... what the hell
    happened to it?  It used to work, but I must have messed it up somehow. */
    VisADException vexc = null;
    RemoteException rexc = null;
    VDisplay.disableAction();
    for (int i=0; i<maps.length; i++) {
      try {
        VDisplay.addMap(maps[i]);
      }
      catch (VisADException exc) {
        vexc = exc;
      }
      catch (RemoteException exc) {
        rexc = exc;
      }
    }
    VDisplay.addReference(DataRef);
    VDisplay.enableAction();
    HasMappings = true;
    if (vexc != null) throw vexc;
    if (rexc != null) throw rexc;
  }

  /** whether other cells are dependent on this one */
  public boolean othersDepend() {
    try {
      return !fm.canBeRemoved(Name);
    }
    catch (FormulaException exc) {
      return false;
    }
  }

  /** clear this cell's mappings */
  public void clearMaps() throws VisADException, RemoteException {
    if (hasMappings()) {
      VDisplay.removeReference(DataRef);
      VDisplay.clearMaps();
      HasMappings = false;
    }
  }

  /** clear this cell's display */
  public void clearDisplay() throws VisADException, RemoteException {
    clearMaps();
    setVDPanel(false);
  }

  /** clear this cell completely */
  public void clearCell() throws VisADException, RemoteException {
    setFormula(null);
    Filename = null;
    RMIAddress = null;
    clearDisplay();
    setData(null);
  }

  /** clear this cell completely and permanently remove it from the
      list of created cells */
  public void destroyCell() throws VisADException, RemoteException {
    clearCell();
    fm.remove(Name);
    SSCellVector.remove(this);
  }

  /** set this cell's Data to data */
  public void setData(Data data) throws VisADException, RemoteException {
    fm.setThing(Name, data);

    if (data != null) {
      // add this Data's RealTypes to FormulaManager variable registry
      Vector v = new Vector();
      getRealTypes(data, v);
      int len = v.size();
      for (int i=0; i<len; i++) {
        RealType rt = (RealType) v.elementAt(i);
        fm.setThing(rt.getName(), new VRealType(rt));
      }
    }
  }

  /** set the BasicSSCell to 2-D or 3-D display with Java2D or Java3D */
  public void setDimension(boolean twoD, boolean java2d)
                           throws VisADException, RemoteException {
    int dim;
    if (!twoD && java2d) return;
    if (!twoD && !java2d) dim = JAVA3D_3D;
    else if (twoD && java2d) dim = JAVA2D_2D;
    else dim = JAVA3D_2D;  // twoD && !java2d
    setDimension(dim);
  }

  private void setDimension(int dim) throws VisADException, RemoteException {
    if (Dimension2D == dim) return;
    Dimension2D = dim;

    // save current mappings for restoration after dimension switch
    ScalarMap[] maps = null;
    if (VDisplay != null) {
      Vector mapVector = VDisplay.getMapVector();
      int mvs = mapVector.size();
      if (mvs > 0) {
        maps = new ScalarMap[mvs];
        for (int i=0; i<mvs; i++) {
          maps[i] = (ScalarMap) mapVector.elementAt(i);
        }
      }
    }

    // remove listener temporarily
    if (DListen != null) VDisplay.removeDisplayListener(DListen);

    // clear display completely
    clearDisplay();

    // switch display dimension
    if (Dimension2D == JAVA3D_3D) {
      if (IsRemote) VDisplay = new DisplayImplJ3D(RemoteVDisplay);
      else VDisplay = new DisplayImplJ3D(Name);
    }
    else if (Dimension2D == JAVA2D_2D) {
      if (IsRemote) VDisplay = new DisplayImplJ2D(RemoteVDisplay);
      else VDisplay = new DisplayImplJ2D(Name);
    }
    else {  // Dimension2D == JAVA3D_2D
      TwoDDisplayRendererJ3D tdr = new TwoDDisplayRendererJ3D();
      if (IsRemote) VDisplay = new DisplayImplJ3D(RemoteVDisplay, tdr);
      else VDisplay = new DisplayImplJ3D(Name, tdr);
    }
    if (!IsRemote) RemoteVDisplay = new RemoteDisplayImpl(VDisplay);

    // reinitialize display
    VDPanel = (JPanel) VDisplay.getComponent();
    if (hasData()) setVDPanel(true);

    // put listener back
    if (DListen != null) VDisplay.addDisplayListener(DListen);

    // put mappings back
    if (maps != null) {
      try {
        setMaps(maps);
      }
      catch (VisADException exc) { }
    }
    notifyListeners(SSCellChangeEvent.DIMENSION_CHANGE);
  }

  /** set the BasicSSCell's formula */
  public void setFormula(String f) throws VisADException, RemoteException {
    String nf = (f == null ? "" : f);
    if (Formula.equals(nf)) return;
    Formula = "";
    fm.assignFormula(Name, nf);
    Formula = nf;
  }

  /** return whether the BasicSSCell is in 2-D display mode */
  public int getDimension() {
    return Dimension2D;
  }

  /** return the associated DataReference object */
  public DataReferenceImpl getDataRef() {
    return DataRef;
  }

  /** return the associated RemoteDataReference object */
  public RemoteDataReferenceImpl getRemoteDataRef() {
    return RemoteDataRef;
  }

  /** return the file name from which the associated Data came, if any */
  public URL getFilename() {
    return Filename;
  }

  /** return the RMI address from which the associated Data came, if any */
  public String getRMIAddress() {
    return RMIAddress;
  }

  /** return the formula for this BasicSSCell, if any */
  public String getFormula() {
    return Formula;
  }

  /** used by toggleWait */
  private JPanel pWait = null;

  /** used by toggleWait */
  private boolean waiting = false;

  /** used by loadData and loadRMI */
  private void toggleWait() {
    if (pWait == null) {
      pWait = new JPanel();
      pWait.setBackground(Color.black);
      pWait.setLayout(new BoxLayout(pWait, BoxLayout.X_AXIS));
      pWait.add(Box.createHorizontalGlue());
      pWait.add(new JLabel("Please wait..."));
      pWait.add(Box.createHorizontalGlue());
    }
    synchronized (Lock) {
      if (waiting) {
        remove(pWait);
        waiting = false;
      }
      else {
        add(pWait);
        waiting = true;
      }
      validate();
      repaint();
    }
  }

  /** import a data object from a given URL */
  public void loadData(URL u) throws BadFormException, IOException,
                                     VisADException, RemoteException {
    if (u == null) return;
    clearDisplay();
    setFormula(null);
    Filename = null;
    RMIAddress = null;
    toggleWait();

    Data data = null;
    try {
      // file detection -- only necessary because some Data Forms
      //                   lack open(URL) capability
      String s = u.toString();
      boolean f = false;
      String file = null;
      if (s.length() >= 6 && s.substring(0, 6).equalsIgnoreCase("file:/")) {
        f = true;
        file = s.substring(6);
      }
      DefaultFamily loader = new DefaultFamily("loader");
      if (f) data = loader.open(file);
      else data = loader.open(u);
      loader = null;
    }
    finally {
      toggleWait();
    }
    if (data != null) {
      setData(data);
      Filename = u;
    }
    else setData(null);
  }

  /** import a data object from a given RMI address, and automatically
      update this cell whenever the remote data object changes */
  public void loadRMI(String s) throws MalformedURLException,
                                       NotBoundException,
                                       AccessException,
                                       RemoteException,
                                       VisADException {
    // example of RMI address: rmi://www.myaddress.com/MyServer/A1
    if (s == null || !s.startsWith("rmi://")) {
      throw new VisADException("RMI address must begin with \"rmi://\"");
    }
    clearDisplay();
    setFormula(null);
    Filename = null;
    RMIAddress = null;
    toggleWait();

    try {
      int len = s.length();
      int end = s.lastIndexOf("/");
      if (end < 6) end = len;
      String server = s.substring(4, end);
      String object = (end < len-1) ? s.substring(end+1, len) : "";
      RemoteServer rs = null;
      rs = (RemoteServer) Naming.lookup(server);
      RemoteDataReference ref = rs.getDataReference(object);
      if (ref == null) {
        throw new VisADException("Could not import remote object called " +
                                 "\"" + object + "\"");
      }
      final RemoteDataReference rref = ref;
      final BasicSSCell cell = this;
      CellImpl lcell = new CellImpl() {
        public void doAction() {
          // update local data when remote data changes
          try {
            cell.setData(rref.getData().local());
          }
          catch (NullPointerException exc) {
            setError("Remote data is null");
          }
          catch (VisADException exc) {
            setError("Could not update remote data");
          }
          catch (RemoteException exc) {
            setError("Unable to import updated remote data");
          }
        }
      };
      RemoteCellImpl rcell = new RemoteCellImpl(lcell);
      rcell.addReference(ref);
    }
    finally {
      toggleWait();
    }
    RMIAddress = s;
  }

  /** export a data object to a given file name, in netCDF format */
  public void saveData(File f, boolean netcdf) throws BadFormException,
                                                      IOException,
                                                      VisADException,
                                                      RemoteException {
    if (f == null || !hasData()) return;
    Saving++;
    try {
      if (netcdf) {
        Plain saver = new Plain();
        saver.save(f.getPath(), DataRef.getData(), true);
        saver = null;
      }
      else {
        VisADForm saver = new VisADForm();
        saver.save(f.getPath(), DataRef.getData(), true);
        saver = null;
      }
    }
    finally {
      Saving--;
    }
  }

  /** whether the cell has data */
  public boolean hasData() {
    return DataRef.getData() != null;
  }

  /** whether the cell has a formula */
  public boolean hasFormula() {
    return !Formula.equals("");
  }

  /** whether the cell has any mappings */
  public boolean hasMappings() {
    if (IsRemote) {
      Vector v = null;
      try {
        v = RemoteVDisplay.getMapVector();
      }
      catch (VisADException exc) { }
      catch (RemoteException exc) { }
      return v != null && !v.isEmpty();
    }
    else return HasMappings;
  }

  /** add a variable */
  public static void createVar(String name, ThingReference tr)
                                            throws VisADException {
    fm.createVar(name, tr);
  }

}

