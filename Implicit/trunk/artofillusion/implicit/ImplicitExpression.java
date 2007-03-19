/* Copyright (C) 2006 by Francois Guillet

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.implicit;

import artofillusion.object.ImplicitObject;
import artofillusion.object.Object3D;
import artofillusion.object.ObjectInfo;
import artofillusion.math.BoundingBox;
import artofillusion.math.Vec3;
import artofillusion.*;
import artofillusion.procedural.ExprModule;
import artofillusion.procedural.PointInfo;
import artofillusion.ui.*;
import artofillusion.animation.Keyframe;

import java.awt.*;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.DataOutputStream;

/**
 * ImplicitExpression is the most simple form of implicit objects.
 * It allows user to enter a mathematical expression to express the field
 * of the implicit object.
 */
public class ImplicitExpression extends ImplicitObject
{
    protected WireframeMesh mesh;
    protected Vec3 scale;
    protected Vec3 box;
    protected BoundingBox bounds;
    protected ExprModule expressionModule;
    protected String expression;
    protected double cutoff, cutoffDistance;
    protected ThreadLocal renderingExpr;

    private static final Property PROPERTIES[] = new Property [] {
      new Property(IPTranslate.text("Expression"), "(1/sqrt(x*x+y*y+z*z) - 1)^2"),
      new Property(IPTranslate.text("Cutoff"), 0.0, Double.MAX_VALUE, 0.2),
      new Property(IPTranslate.text("CutoffDistance"), 0.0, Double.MAX_VALUE, 1),
    };

    /**
     * Creates an implicit expression with a given size. The default
     * field expression is a sphere of radius 0.5
     * @param xsize Size along x axis
     * @param ysize Size along y axis
     * @param zsize Size along z axis
     */
    public ImplicitExpression(double xsize, double ysize, double zsize)
    {
        super();
	expressionModule = new ExprModule(new Point(0, 0));
        try
        {
            setExpression( "(1/sqrt(x*x+y*y+z*z) - 1)^2" );
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        cutoff = 0.2;
        cutoffDistance = 1;
        box = new Vec3(2, 2, 2);
        bounds = new BoundingBox(-1, 1, -1, 1, -1, 1);
        scale = new Vec3(1, 1, 1);
        setSize(xsize,ysize,zsize);
        initWireframeMesh();
        initThreadLocal();
    }

    public ImplicitExpression(DataInputStream in, Scene theScene) throws IOException, InvalidObjectException
    {
        super(in, theScene);
	short version = in.readShort();
        if (version < 0 || version > 0)
            throw new InvalidObjectException("");
        expression = in.readUTF();
        expressionModule = new ExprModule(new Point(0,0));
        expressionModule.readFromStream(in, theScene);
        cutoff = in.readDouble();
        cutoffDistance = in.readDouble();
        scale = new Vec3(in);
        box = new Vec3(in);
        bounds = new BoundingBox(-box.x/2.0, box.x/2.0, -box.y/2.0, box.y/2.0, -box.z/2.0, box.z/2.0);
        initWireframeMesh();
        initThreadLocal();
    }


    /**
     * Reinitialize the ThreadLocals that holds copies of the modules during rendering.
     */
    private void initThreadLocal()
    {
      renderingExpr = new ThreadLocal() {
        protected Object initialValue()
        {
          return expressionModule.duplicate();
        }
      };
    }

    public void writeToFile(DataOutputStream out, Scene theScene) throws IOException
    {
	super.writeToFile(out, theScene);
	out.writeShort(0);
        out.writeUTF(expression);
        expressionModule.writeToStream(out, theScene);
        out.writeDouble(cutoff);
        out.writeDouble(cutoffDistance);
        scale.writeToFile(out);
        box.writeToFile(out);
    }

    public double getFieldValue(double x, double y, double z, double size, double time)
    {
        if (cutoffDistance >= 0)
        {
            if ( x*x + y*y + z*z > cutoffDistance*cutoffDistance)
                return 0;
        }

        ExprModule module = (ExprModule) renderingExpr.get();
        PointInfo p = new PointInfo();
        p.x = x*scale.x;
        p.y = y*scale.y;
        p.z = z*scale.z;
        p.xsize = size*scale.x;
        p.ysize = size*scale.y;
        p.zsize = size*scale.z;
        p.t = time;
        module.init(p);
        return module.getAverageValue(0, 0.0);
    }

    public void getFieldGradient(double x, double y, double z, double size, double time, Vec3 grad)
    {
        if (cutoffDistance >= 0)
        {
            if ( x*x + y*y + z*z > cutoffDistance*cutoffDistance)
                return;
        }
        ExprModule valueModule = (ExprModule) renderingExpr.get();
        PointInfo p = new PointInfo();
        p.x = x*scale.x;
        p.y = y*scale.y;
        p.z = z*scale.z;
        p.xsize = size*scale.x;
        p.ysize = size*scale.y;
        p.zsize = size*scale.z;
        p.t = time;
        valueModule.init(p);
        valueModule.getValueGradient(0, grad, 0.0);
        grad.x /= scale.x;
        grad.y /= scale.y;
        grad.z /= scale.z;
    }

    public void setCutoff(double cutoff)
    {
        this.cutoff = cutoff;
    }

    public double getCutoff()
    {
        return cutoff;
    }

     public void setCutoffDistance(double cutoffDistance)
    {
        this.cutoffDistance = cutoffDistance;
    }

    public double getCutoffDistance()
    {
        return cutoffDistance;
    }

    /**
     * No rendering mesh for this kind of object
     * @return true.
     */
    public boolean getPreferDirectRendering()
    {
        return true;
    }

    public Object3D duplicate()
    {
        ImplicitExpression obj = new ImplicitExpression(0, 0, 0);
        obj.bounds = new BoundingBox(bounds);
        obj.scale = new Vec3(scale);
        obj.box = new Vec3(box);
        obj.expressionModule = (ExprModule) expressionModule.duplicate();
        obj.cutoff = cutoff;
        obj.cutoffDistance = cutoffDistance;
        obj.initThreadLocal();
        obj.copyTextureAndMaterial(this);
        return (Object3D)obj;
    }

    public void copyObject(Object3D obj)
    {
        ImplicitExpression wrapper = (ImplicitExpression)obj;
        bounds = new BoundingBox( wrapper.bounds );
        scale = new Vec3( wrapper.scale );
        box = new Vec3(wrapper.box);
        expressionModule = (ExprModule) wrapper.expressionModule.duplicate();
        cutoff = wrapper.cutoff;
        cutoffDistance = wrapper.cutoffDistance;
        initThreadLocal();
        copyTextureAndMaterial(obj);
    }

    public BoundingBox getBounds()
    {
        return bounds;
    }

    /**
     * Sets the box in which the implicit surface equation is considered.
     * Not to be confused with bounds which scales the box
     * @param xs Box size (X axis)
     * @param ys Box size (Y axis)
     * @param zs Box size (Z axis)
     */
    public void setBox( double xs, double ys, double zs)
    {
        box.x = xs;
        box.y = ys;
        box.z = zs;
        bounds = new BoundingBox( -xs/2, xs/2, -ys/2, ys/2, -zs/2, zs/2 );
        initWireframeMesh();
    }

    /**
     * Sets the box in which the implicit surface equation is considered.
     * Not to be confused with bounds which scales the box
     * @param b Box
     */
    public void setBox( Vec3 b)
    {
        setBox(b.x, b.y, b.z);
    }


    /**
     *  Display a window in which the user can edit this object.
     *
     *@param  parent  the window from which this command is being invoked
     *@param  info    the ObjectInfo corresponding to this object
     *@param  cb      a callback which will be executed when editing is
     *      complete. If the user cancels the operation, it will not be called.
     */
    public void edit( EditingWindow parent, ObjectInfo info, Runnable cb )
    {
        ImplicitExpressionWindow ed = new ImplicitExpressionWindow( parent, "Implicit Object '" + info.name + "'",
                info, cb );
        ed.setVisible( true );
        cb.run();
        initThreadLocal();
    }

    /**
     * Sets the size of the object. The field value is rescaled accordingly.
     * 
     * @param x
     * @param y
     * @param z
     */
    public void setSize(double x, double y, double z)
    {
        Vec3 ns = new Vec3( x/box.x, y/box.y, z/box.z);
        bounds = new BoundingBox( -x/2, x/2, -y/2, y/2, -z/2, z/2 );
        box.x = x;
        box.y = y;
        box.z = z;
        scale.x /= ns.x;
        scale.y /= ns.y;
        scale.z /= ns.z;
        initWireframeMesh();
    }

    private void initWireframeMesh()
    {
        Vec3[] vert = bounds.getCorners();
        int[] from = new int [] {0, 2, 3, 1, 4, 6, 7, 5, 0, 1, 2, 3};
        int[] to = new int [] {2, 3, 1, 0, 6, 7, 5, 4, 4, 5, 6, 7};
        mesh = new WireframeMesh(vert, from, to);
    }

    public WireframeMesh getWireframeMesh()
    {
        return mesh;
    }

    public RenderingMesh getRenderingMesh()
    {
        return null;
    }

    public Keyframe getPoseKeyframe()
    {
        return null;
    }

    public void applyPoseKeyframe(Keyframe k)
    {

    }

    public boolean isEditable()
    {
        return true;
    }

    /**
     * Sets the mathematical expression that represents the field for
     * this implicit object
     *
     * @param expr The mathematical expression
     * @throws Exception An exception is thrown if the expression is invalid.
     */
    public void setExpression(String expr) throws Exception
    {
        expressionModule.setExpr( expr );
        expression = expr;
        initThreadLocal();
    }

    /**
     * Returns the expression currently stored in the ImplicitExpression object
     * @return expression
     */
    public String getExpression()
    {
        return expression;
    }

    public Property[] getProperties()
    {
        return (Property []) PROPERTIES.clone();
    }

    public Object getPropertyValue(int index)
    {
        switch (index)
        {
            case 0:
                return expression;
            case 1:
                return new Double(cutoff);
            case 2:
                return new Double(cutoffDistance);
        }
        return null;
    }

    public void setPropertyValue(int index, Object value)
    {
        if (index == 0)
        {
            try
            {
                setExpression((String) value);
            }
            catch (Exception ex)
            {
            }
        }
        else if (index == 1)
            setCutoff(((Number) value).doubleValue());
        else if (index == 2)
            setCutoffDistance(((Number) value).doubleValue());
    }
}
