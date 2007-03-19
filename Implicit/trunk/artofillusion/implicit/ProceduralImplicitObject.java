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
import artofillusion.math.*;
import artofillusion.*;
import artofillusion.animation.Keyframe;
import artofillusion.ui.*;
import artofillusion.procedural.*;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.InvalidObjectException;

import buoy.widget.Widget;

/**
 * Procedural objects are implicit objects for which the surface equation is computed
 * using a procedure.
 */
public class ProceduralImplicitObject extends ImplicitObject implements ProcedureOwner
{
    private WireframeMesh mesh;
    private Vec3 scale;
    private Vec3 box;
    private Procedure procedure;
    private String procedureName;
    private Runnable callback;
    private double cutoff;
    private ParameterModule[] parameterModules;
    private double[] parameters;
    private ThreadLocal renderingProc;
    private BoundingBox bounds;

    public ProceduralImplicitObject(double xsize, double ysize, double zsize)
    {
	super();
        box = new Vec3(2.0, 2.0, 2.0);
        bounds = new BoundingBox(-box.x/2, box.x/2.0, -box.y/2.0, box.y/2.0, -box.z/2.0, box.z/2.0);
        scale = new Vec3(1, 1, 1);
        procedure = createProcedure();
        procedureName = IPTranslate.text("proceduralObject");
        cutoff = 1.0;
        initWireframeMesh();
        initThreadLocal();
    }

    public ProceduralImplicitObject(DataInputStream in, Scene theScene) throws IOException, InvalidObjectException
    {
        super(in, theScene);
        short version = in.readShort();
        if (version < 0 || version > 0)
            throw new InvalidObjectException("");
        procedureName = in.readUTF();
        procedure = createProcedure();
        procedure.readFromStream(in, theScene);
        cutoff = in.readDouble();
        scale = new Vec3(in);
        box = new Vec3(in);
        bounds = new BoundingBox(-box.x/2, box.x/2.0, -box.y/2.0, box.y/2.0, -box.z/2.0, box.z/2.0);
        initWireframeMesh();
        initThreadLocal();
    }

    public void writeToFile(DataOutputStream out, Scene theScene) throws IOException
    {
        super.writeToFile(out, theScene);
        out.writeShort(0);
        out.writeUTF(procedureName);
        procedure.writeToStream(out, theScene);
        out.writeDouble(cutoff);
        scale.writeToFile(out);
        box.writeToFile(out);
    }


    private void initWireframeMesh()
    {
        Vec3[] vert = bounds.getCorners();
        int[] from = new int [] {0, 2, 3, 1, 4, 6, 7, 5, 0, 1, 2, 3};
        int[] to = new int [] {2, 3, 1, 0, 6, 7, 5, 4, 4, 5, 6, 7};
        mesh = new WireframeMesh(vert, from, to);
    }


    /**
     * Create a Procedure object for this texture.
     */

    private Procedure createProcedure()
    {
        return new Procedure(new OutputModule [] {
                new OutputModule(IPTranslate.text("proceduralValue"), "1.0", 0.0, null, IOPort.NUMBER)
        });
    }

    /**
     * Reinitialize the ThreadLocal that holds copies of the Procedure during rendering.
     */

    private void initThreadLocal()
    {
      renderingProc = new ThreadLocal() {
        protected Object initialValue()
        {
          Procedure localProc = createProcedure();
          localProc.copy(procedure);
          return localProc;
        }
      };
    }

    public double getFieldValue(double x, double y, double z, double size, double time)
    {
        Procedure pr = (Procedure) renderingProc.get();
        PointInfo p = new PointInfo();
        p.x = x*scale.x;
        p.y = y*scale.y;
        p.z = z*scale.z;
        p.xsize = size*scale.x;
        p.ysize = size*scale.y;
        p.zsize = size*scale.z;
        p.t = time;
        p.param = parameters;
        if (parameters != null)
        {
            for (int i = 0; i < parameters.length; i++)
                parameters[i] = parameterModules[i].getDefaultValue();
        }
        OutputModule output[] = pr.getOutputModules();
        pr.initForPoint(p);
        //System.out.println(output[0].getAverageValue(0, 0.0));
        return output[0].getAverageValue(0, 0.0);
    }

    public void getFieldGradient(double x, double y, double z, double size, double time, Vec3 grad)
    {
      Procedure pr = (Procedure) renderingProc.get();
        PointInfo p = new PointInfo();
        p.x = x*scale.x;
        p.y = y*scale.y;
        p.z = z*scale.z;
        p.xsize = size*scale.x;
        p.ysize = size*scale.y;
        p.zsize = size*scale.z;
        p.param = parameters;
        if (parameters != null)
        {
            for (int i = 0; i < parameters.length; i++)
                parameters[i] = parameterModules[i].getDefaultValue();
        }
        p.t = time;
        OutputModule output[] = pr.getOutputModules();
        pr.initForPoint(p);
        output[0].getValueGradient(0, grad, 0.0);
        grad.x /= scale.x;
        grad.y /= scale.y;
        grad.z /= scale.z;
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
        ProceduralImplicitObject obj = new ProceduralImplicitObject(0, 0, 0);
        obj.scale = new Vec3(scale);
        obj.box = new Vec3(box);
        obj.bounds = new BoundingBox(bounds);
        obj.procedure.copy(procedure);
        obj.cutoff = cutoff;
        obj.initWireframeMesh();
        obj.initThreadLocal();
        obj.copyTextureAndMaterial(this);
        return (Object3D)obj;
    }

    public void copyObject(Object3D obj)
    {
        ProceduralImplicitObject proceduralImplicit = (ProceduralImplicitObject)obj;
        scale = new Vec3( proceduralImplicit.scale );
        box = new Vec3(proceduralImplicit.box);
        bounds = new BoundingBox(proceduralImplicit.bounds);
        procedure.copy(proceduralImplicit.procedure);
        cutoff = proceduralImplicit.cutoff;
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

    public Vec3 getBox()
    {
        return box;
    }

    public Procedure getProcedure()
    {
        return procedure;
    }

    public void setProcedure(Procedure procedure)
    {
        this.procedure = procedure;
        initThreadLocal();
    }

    /**
     * This method updates parameters list
     * (parameter modules in the procedure)
     * @return Number of parameters specified in the procedure
     */
    public int fetchParameters()
    {
        Module[] modules = procedure.getModules();
        int count = 0;
        for (int i = 0; i < modules.length; i++)
        {
            if (modules[i] instanceof ParameterModule)
                ++count;
        }
        if (count > 0)
        {
            parameterModules = new ParameterModule[count];
            count = 0;
            for (int i = 0; i < modules.length; i++)
            {
                if (modules[i] instanceof ParameterModule)
                {
                    parameterModules[count] = (ParameterModule)modules[i];
                    parameterModules[count].setIndex(count);
                    count++;
                }
            }
            parameters = new double[count];
        }
        else
        {
            parameterModules = null;
            parameters = null;
        }
        return count;
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
        new ProcedureEditor(procedure, this, ((LayoutWindow)parent).getScene());
        callback = cb;
        //cb.run();
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

    /* ProcedureOwner interface */

    /** Get the title of the procedure's editing window. */
    public String getWindowTitle()
    {
        return procedureName;
    }

    /** Create an object which displays a preview of the procedure. */
    public Object getPreview(ProcedureEditor editor)
    {
      FloatingDialog dlg = new FloatingDialog(editor.getParentFrame(), "Preview", false);
      MaterialPreviewer preview = new MaterialPreviewer(new ObjectInfo(this, new CoordinateSystem(), ""), 200, 160);
      dlg.setContent(preview);
      dlg.pack();
      Rectangle parentBounds = editor.getParentFrame().getBounds();
      Rectangle location = dlg.getBounds();
      location.y = parentBounds.y;
      location.x = parentBounds.x+parentBounds.width;
      dlg.setBounds(location);
      dlg.setVisible(true);
      return preview;
    }

    /** Update the display of the preview. */

    public void updatePreview(Object preview)
    {
      initThreadLocal();
      ((MaterialPreviewer) preview).render();
    }

    /** Dispose of the preview object when the editor is closed. */

    public void disposePreview(Object preview)
    {
      ((FloatingDialog) ((MaterialPreviewer) preview).getParent()).dispose();
    }

    /** Determine whether the procedure may contain View Angle modules. */

    public boolean allowViewAngle()
    {
        return false;
    }

    /** Determine whether the procedure may contain Parameter modules. */

    public boolean allowParameters()
    {
        return true;
    }

    /** Determine whether the procedure may be renamed. */

    public boolean canEditName()
    {
        return true;
    }

    /** Returns the name of the procedure **/
    public String getName()
    {
        return procedureName;
    }

    /** Sets the name of the procedure **/
    public void setName(String name)
    {
        procedureName = name;
    }

    /** This is called when the user clicks OK in the procedure editor. */
    public void acceptEdits(ProcedureEditor editor)
    {
        initThreadLocal();
        //check
        /*System.out.println( cutoff + " : cutoff");
      System.out.println( getFieldValue(0.2,0.25,0.23,0.0));
      Vec3 grad = new Vec3();
      getFieldGradient(0.2,0.25,0.23,0.0, grad);
      System.out.println( grad );*/
        if (callback != null)
        {
            callback.run();
            callback = null;
        }
        //editor.getEditingWindow().updateImage();
    }

    /** Display the Properties dialog. */

    public void editProperties(ProcedureEditor editor)
    {
        ValueField xSizeField = new ValueField(box.x, ValueField.NONNEGATIVE);
        ValueField ySizeField = new ValueField(box.y, ValueField.NONNEGATIVE);
        ValueField zSizeField = new ValueField(box.z, ValueField.NONNEGATIVE);
        ValueField cutoffField = new ValueField(cutoff, ValueField.NONNEGATIVE);
        int count = fetchParameters();
        System.out.println(count);
        Widget[] widgets = new Widget[4+count];
        String[] labels = new String[4+count];
        widgets[0] = xSizeField;
        widgets[1] = ySizeField;
        widgets[2] = zSizeField;
        widgets[3] = cutoffField;
        labels[0] = IPTranslate.text("xBounds");
        labels[1] = IPTranslate.text("yBounds");
        labels[2] = IPTranslate.text("zBounds");
        labels[3] = IPTranslate.text("cutoff");
        if (count > 0)
            for (int i = 0; i < count; i++ )
            {
                widgets[4+i] =  new ValueField(parameterModules[i].getDefaultValue(), ValueField.NONE);
                labels[4+i] =  parameterModules[i].getName();
            }
        ComponentsDialog dlg = new ComponentsDialog(editor.getParentFrame(), IPTranslate.text("editObjectParameters"),
                widgets, labels );
        if (!dlg.clickedOk())
            return;
        editor.saveState(false);
        setBox( xSizeField.getValue(), ySizeField.getValue(), zSizeField.getValue() );
        cutoff = cutoffField.getValue();
        if (count > 0)
            for (int i = 0; i < count; i++ )
                parameterModules[i].setDefaultValue(((ValueField)widgets[i+4]).getValue());
        editor.updatePreview();
    }
}
