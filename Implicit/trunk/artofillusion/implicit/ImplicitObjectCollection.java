package artofillusion.implicit;

import artofillusion.object.ImplicitObject;
import artofillusion.object.Object3D;
import artofillusion.object.NullObject;
import artofillusion.*;
import artofillusion.animation.Keyframe;
import artofillusion.math.Vec3;
import artofillusion.math.BoundingBox;
import artofillusion.math.CoordinateSystem;

import java.util.Vector;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/* Copyright (C) 2006 by Francois Guillet

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

/**
 * An implicit object collection is a collection of implicit objects which all
 * add up to form a single object. Any kind of implicit object can be added to a collection.
 * Each object will be translated to the origin of the coordinate system it is associated to.
 * Later feature may include axis rotation as per specified in the coordinate systems.
 * Since it is not possible to guess a satisfying box for rendering computations from
 * a sum of implicit objects, you must use setBox or setBounds to set the rendering
 * box.
 *
 */

public class ImplicitObjectCollection extends ImplicitObject
{
    private Vector collection;
    private ThreadLocal renderingCollection;
    private WireframeMesh mesh;
    private Vec3 scale;
    private Vec3 box;
    private double cutoff;
    private BoundingBox bounds;

    private static final Property PROPERTIES[] = new Property [] {
            new Property(IPTranslate.text("Cutoff"), 0.0, Double.MAX_VALUE, 1.0),
    };

    /**
     * Creates an implicit object collection with a default size of 2, 2, 2.
     */
    public ImplicitObjectCollection()
    {
        collection = new Vector();
        cutoff = 1.0;
        scale = new Vec3(1.0, 1.0, 1.0);
        box = new Vec3(2, 2, 2);
        bounds = new BoundingBox(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        initWireframeMesh();
        initThreadLocal();
    }

    public ImplicitObjectCollection(DataInputStream in, Scene theScene) throws IOException, InvalidObjectException {
        super(in, theScene);
        short version = in.readShort();
        if (version < 0 || version > 0)
            throw new InvalidObjectException("");
        cutoff = in.readDouble();
        scale = new Vec3(in);
        box = new Vec3(in);
        bounds = new BoundingBox(-box.x/2, box.x/2.0, -box.y/2.0, box.y/2.0, -box.z/2.0, box.z/2.0);
        int count = in.readInt();
        collection = new Vector();
        if (count > 0)
            for (int i = 0; i < count; i++)
                collection.add(new ImplicitObjectWrapper(in, theScene));
        initWireframeMesh();
        initThreadLocal();
    }

    private void initWireframeMesh()
    {
        Vec3[] vert = bounds.getCorners();
        int[] from = new int [] {0, 2, 3, 1, 4, 6, 7, 5, 0, 1, 2, 3};
        int[] to = new int [] {2, 3, 1, 0, 6, 7, 5, 4, 4, 5, 6, 7};
        mesh = new WireframeMesh(vert, from, to);
    }

    public void writeToFile(DataOutputStream out, Scene theScene) throws IOException
    {
        super.writeToFile(out, theScene);
        out.writeShort(0);
        out.writeDouble(cutoff);
        scale.writeToFile(out);
        box.writeToFile(out);
        out.writeInt(collection.size());
        ImplicitObjectWrapper obj;
        for (int i = 0; i < collection.size(); i++)
        {
            obj = (ImplicitObjectWrapper)collection.elementAt(i);
            obj.writeToFile(out, theScene);
        }
    }

    /**
     * Sets the cutoff for the collection
     **/
    public void setCutoff(double cutoff)
    {
        this.cutoff = cutoff;
    }

    public double getCutoff()
    {
        return cutoff;
    }

    public boolean getPreferDirectRendering()
    {
        return true;
    }

    public Object3D duplicate()
    {
        ImplicitObjectCollection obj = new ImplicitObjectCollection();
        obj.bounds = new BoundingBox(bounds);
        obj.scale = new Vec3(scale);
        obj.box = new Vec3(box);
        obj.cutoff = cutoff;
        if ( collection.size() != 0)
            for (int i = 0; i < collection.size(); i++)
                obj.collection.add(((ImplicitObjectWrapper)collection.elementAt(i)).duplicate());
        obj.initThreadLocal();
        obj.copyTextureAndMaterial(this);
        return (Object3D)obj;
    }

    public void copyObject(Object3D obj)
    {
        ImplicitObjectCollection col = (ImplicitObjectCollection)obj;
        bounds = new BoundingBox( col.bounds );
        scale = new Vec3( col.scale );
        box = new Vec3(col.box);
        cutoff = col.cutoff;
        if ( col.collection.size() != 0)
            for (int i = 0; i < col.collection.size(); i++)
                collection.add(((ImplicitObjectWrapper)col.collection.elementAt(i)).duplicate());
        initThreadLocal();
        copyTextureAndMaterial(obj);
    }

    /**
     * Sets the overall size of the collection
     * All objects are resized accordingly.
     * @param x Size along x axis
     * @param y Size along y axis
     * @param z Size along z axis
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
    
    public BoundingBox getBounds()
    {
        return bounds;
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

    /**
     * An implicit object collection can't be edited.
     * @return false
     */
    public boolean isEditable()
    {
        return false;
    }

    /**
     * Reinitialize the ThreadLocals that holds copies of the implicit expressions during rendering.
     */
    private void initThreadLocal()
    {
        renderingCollection = new ThreadLocal() {
            protected Object initialValue()
            {
                Vector renderCol = new Vector();
                if ( collection.size() != 0)
                    for (int i = 0; i < collection.size(); i++)
                        renderCol.add(((ImplicitObjectWrapper)collection.elementAt(i)).duplicate());
                return renderCol;
            }
        };
    }

    public double getFieldValue(double x, double y, double z, double size, double time)
    {
        Vector col = (Vector) renderingCollection.get();
        double x0 = x*scale.x;
        double y0 = y*scale.y;
        double z0 = z*scale.z;
        double xc, yc ,zc;
        double val = 0.0;
        BoundingBox bounds;
        for (int i = 0; i < col.size(); i++)
        {
            ImplicitObjectWrapper wrapper = (ImplicitObjectWrapper) col.elementAt(i);
            xc = x0 - wrapper.coords.getOrigin().x;
            yc = y0 - wrapper.coords.getOrigin().y;
            zc = z0 - wrapper.coords.getOrigin().z;
            bounds = wrapper.object.getBounds();
            if (bounds.contains(new Vec3(xc, yc, zc)))
                val += wrapper.object.getFieldValue(xc, yc, zc, size, time);
        }
        return val;
    }

    public void getFieldGradient(double x, double y, double z, double size, double time, Vec3 grad)
    {
        Vector col = (Vector) renderingCollection.get();
        double x0 = x*scale.x;
        double y0 = y*scale.y;
        double z0 = z*scale.z;
        double xc, yc ,zc;
        Vec3 gradc = new Vec3();
        grad.x = grad.y = grad.z = 0;
        for (int i = 0; i < col.size(); i++)
        {
            ImplicitObjectWrapper wrapper = (ImplicitObjectWrapper) col.elementAt(i);
            xc = x0 - wrapper.coords.getOrigin().x;
            yc = y0 - wrapper.coords.getOrigin().y;
            zc = z0 - wrapper.coords.getOrigin().z;
            bounds = wrapper.object.getBounds();
            if (bounds.contains(new Vec3(xc, yc, zc)))
            {
                gradc.x = gradc.y = gradc.z = 0;
                wrapper.object.getFieldGradient(xc, yc, zc, size, time, gradc);
                grad.add(gradc);
            }
        }
        grad.x /= scale.x;
        grad.y /= scale.y;
        grad.z /= scale.z;
    }

    /**
     * ImplicitObjectWrapper is a class that holds an ImplicitObject
     * along with the coordinate system used to translate
     * the implicit object.
     */
    protected class ImplicitObjectWrapper
    {
        private CoordinateSystem coords;
        private ImplicitObject object;

        public ImplicitObjectWrapper(ImplicitObject obj, CoordinateSystem coords)
        {
            object = (ImplicitObject)obj.duplicate();
            this.coords = coords.duplicate();
        }

        public CoordinateSystem getCoordinateSystem()
        {
            return coords;
        }

        public void setCoordinateSytem(CoordinateSystem coords)
        {
            this.coords = coords;
        }

        public ImplicitObjectWrapper duplicate()
        {
            return new ImplicitObjectWrapper(object, coords);
        }


        public ImplicitObjectWrapper(DataInputStream in, Scene theScene) throws IOException, InvalidObjectException
        {
            String classname = in.readUTF();
            int len = in.readInt();
            byte bytes[] = new byte [len];
            in.readFully(bytes);
            try
            {
                Class cls = ModellingApp.getClass(classname);
                Constructor con = cls.getConstructor(new Class [] {DataInputStream.class, Scene.class});
                object = (ImplicitObject) con.newInstance(new Object [] {new DataInputStream(new ByteArrayInputStream(bytes)), theScene });
            }
            catch (Exception ex)
            {
                if (ex instanceof InvocationTargetException)
                    ((InvocationTargetException) ex).getTargetException().printStackTrace();
                else
                    ex.printStackTrace();
                object = null;
            }
            coords = new CoordinateSystem(in);
            scale = new Vec3(in);
            initThreadLocal();
        }

        public void writeToFile(DataOutputStream out, Scene theScene) throws IOException
        {
            out.writeUTF(object.getClass().getName());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            object.writeToFile(new DataOutputStream(bos), theScene);
            byte bytes[] = bos.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes, 0, bytes.length);
            coords.writeToFile(out);
            scale.writeToFile(out);
        }
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
     * Adds an implicit object to the object collection
     *
     * @param object The object to add
     * @param coords The coordinates at which the expression must be placed
     */
    public void addObject(ImplicitObject object, CoordinateSystem coords)
    {
        collection.add(new ImplicitObjectWrapper(object, coords));
        initThreadLocal();
    }

    /**
     * Returns the implicit object at a given index in the collection
     * @param index
     * @return The implicit object at index
     */
    public ImplicitObject objectAt(int index)
    {
        return ((ImplicitObjectWrapper)collection.elementAt(index)).object;
    }

    /**
     * Returns the coordinates of an object at a given index in the collection
     * @param index
     * @return The coordinates of the implicit object
     */
    public CoordinateSystem coordsOfObjectAt(int index)
    {
        return ((ImplicitObjectWrapper)collection.elementAt(index)).coords;
    }

    /**
     * Call this method to notify a property of an implicit object
     * has been changed (mathematical expression, size, etc).
     * You typically need to call this method after a change
     * to an implicit object obtained suing a call to objectAt.
     */
    public void updateObjects()
    {
        initThreadLocal();
    }

    /**
     * Removes the object at index
     * @param index
     */
    public void removeObjectAt(int index)
    {
        if (index < collection.size())
            collection.remove( index );
        initThreadLocal();
    }

    /**
     * Clears the object list
     */
    public void removeAllObjects()
    {
        collection.clear();
        initThreadLocal();
    }
}


