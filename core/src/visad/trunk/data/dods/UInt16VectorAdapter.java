package visad.data.dods;

import dods.dap.*;
import java.rmi.RemoteException;
import visad.*;

public final class UInt16VectorAdapter
    extends FloatVectorAdapter
{
    private final Valuator	valuator;

    public UInt16VectorAdapter(
	    UInt16PrimitiveVector vector,
	    AttributeTable table,
	    VariableAdapterFactory factory)
	throws VisADException, RemoteException
    {
	super(vector, table, factory);
	valuator = Valuator.valuator(table);
    }

    public float[] getFloats(PrimitiveVector vec)
    {
	UInt16PrimitiveVector	vector = (UInt16PrimitiveVector)vec;
	float[]			values = new float[vector.getLength()];
	for (int i = 0; i < values.length; ++i)
	    values[i] = vector.getValue(i);
	return valuator.process(values);
    }
}
