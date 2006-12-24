/*
 *  Copyright (C) 2003 by Peter Eastman
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import artofillusion.TextureParameter;
import artofillusion.texture.ParameterValue;

/**
 *  This class defines a scalar parameter who value is defined on each face of a
 *  mesh.
 *
 *@author     Francois Guillet
 *@created    5 février 2005
 */

public class PMFaceParameterValue implements ParameterValue
{
    private double value[];


    /**
     *  Create a new PMFaceParameterValue object.
     *
     *@param  val  Description of the Parameter
     */

    public PMFaceParameterValue( double val[] )
    {
        value = val;
    }


    /**
     *  Create a new PMFaceParameterValue for a triangle mesh, and initialize it
     *  to appropriate default values.
     *
     *@param  mesh   Description of the Parameter
     *@param  param  Description of the Parameter
     */

    public PMFaceParameterValue( PolyMesh mesh, TextureParameter param )
    {
        value = new double[mesh.getFaces().length];
        for ( int i = 0; i < value.length; i++ )
            value[i] = param.defaultVal;
    }


    /**
     *  Get the list of parameter values.
     *
     *@return    The value value
     */

    public double[] getValue()
    {
        return value;
    }


    /**
     *  Set the list of parameter values.
     *
     *@param  val  The new value value
     */

    public void setValue( double val[] )
    {
        value = val;
    }


    /**
     *  Get the value of the parameter at a particular point in a particular
     *  triangle.
     *
     *@param  tri  Description of the Parameter
     *@param  v1   Description of the Parameter
     *@param  v2   Description of the Parameter
     *@param  v3   Description of the Parameter
     *@param  u    Description of the Parameter
     *@param  v    Description of the Parameter
     *@param  w    Description of the Parameter
     *@return      The value value
     */

    public double getValue( int tri, int v1, int v2, int v3, double u, double v, double w )
    {
        return value[tri];
    }


    /**
     *  Get the average value of the parameter over the entire surface.
     *
     *@return    The averageValue value
     */

    public double getAverageValue()
    {
        double avg = 0.0;
        for ( int i = 0; i < value.length; i++ )
            avg += value[i];
        return ( avg / value.length );
    }


    /**
     *  Create a duplicate of this object.
     *
     *@return    Description of the Return Value
     */

    public ParameterValue duplicate()
    {
        double d[] = new double[value.length];
        System.arraycopy( value, 0, d, 0, value.length );
        return new PMFaceParameterValue( d );
    }


    /**
     *  Determine whether this object represents the same set of values as
     *  another one.
     *
     *@param  o  Description of the Parameter
     *@return    Description of the Return Value
     */

    public boolean equals( Object o )
    {
        if ( !( o instanceof PMFaceParameterValue ) )
            return false;
        PMFaceParameterValue v = (PMFaceParameterValue) o;
        if ( v.value.length != value.length )
            return false;
        for ( int i = 0; i < value.length; i++ )
            if ( v.value[i] != value[i] )
                return false;
        return true;
    }


    /**
     *  Write out a serialized representation of this object to a stream.
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */

    public void writeToStream( DataOutputStream out )
        throws IOException
    {
        out.writeInt( value.length );
        for ( int i = 0; i < value.length; i++ )
            out.writeDouble( value[i] );
    }


    /**
     *  Reconstruct a serialized object.
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */

    public PMFaceParameterValue( DataInputStream in )
        throws IOException
    {
        value = new double[in.readInt()];
        for ( int i = 0; i < value.length; i++ )
            value[i] = in.readDouble();
    }
}

