/*
 *  Copyright (C) 2002-2004 by Peter Eastman
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

import artofillusion.ModellingApp;
import artofillusion.Scene;
import artofillusion.math.Mat4;
import artofillusion.math.RGBColor;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.MeshVertex;
import artofillusion.object.ObjectInfo;
import artofillusion.texture.Mapping2D;
import artofillusion.texture.TextureSpec;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.Translate;
import artofillusion.ui.ValueField;
import artofillusion.ui.ValueSlider;
import buoy.event.ValueChangedEvent;
import buoy.widget.BCheckBox;
import buoy.widget.BFileChooser;
import buoy.widget.BFrame;
import buoy.widget.BStandardDialog;
import buoy.widget.Widget;

/**
 *  PMOBJExporter contains the actual routines for exporting OBJ files for
 *  PolyMeshes.
 *
 *@author     pims
 *@created    13 juin 2005
 */

public class PMOBJExporter
{
    /**
     *  Display a dialog which allows the user to export a scene to an OBJ file.
     *
     *@param  parent    Description of the Parameter
     *@param  theScene  Description of the Parameter
     */

    public static void exportFile( BFrame parent, Scene theScene )
    {

        if ( theScene.getSelection().length == 0 )
            return;
        boolean valid = false;
        for ( int i = 0; i < theScene.getNumObjects(); i++ )
        {
            ObjectInfo info = theScene.getObject( i );
            if ( info.selected && info.object instanceof PolyMesh )
                valid = true;
        }
        if ( !valid )
            return;

        // Display a dialog box with options on how to export the scene.

        final ValueField widthField = new ValueField( 200.0, ValueField.INTEGER + ValueField.POSITIVE );
        final ValueField heightField = new ValueField( 200.0, ValueField.INTEGER + ValueField.POSITIVE );
        final ValueSlider qualitySlider = new ValueSlider( 0.0, 1.0, 100, 0.5 );
        final BCheckBox mtlBox = new BCheckBox( Translate.text( "writeTexToMTL" ), false );
        mtlBox.addEventLink( ValueChangedEvent.class,
            new Object()
            {
                void processEvent()
                {
                    widthField.setEnabled( mtlBox.getState() );
                    heightField.setEnabled( mtlBox.getState() );
                    qualitySlider.setEnabled( mtlBox.getState() );
                }
            } );
        mtlBox.dispatchEvent( new ValueChangedEvent( mtlBox ) );
        ComponentsDialog dlg;
        dlg = new ComponentsDialog( parent, Translate.text( "exportToOBJ" ),
                new Widget[]{mtlBox, Translate.label( "imageSizeForTextures" ), widthField, heightField, qualitySlider},
                new String[]{null, null, Translate.text( "Width" ), Translate.text( "Height" ), Translate.text( "imageQuality" )} );
        if ( !dlg.clickedOk() )
            return;

        // Ask the user to select the output file.

        BFileChooser fc = new BFileChooser( BFileChooser.SAVE_FILE, Translate.text( "exportToOBJ" ) );
        fc.setSelectedFile( new File( "Untitled.obj" ) );
        if ( ModellingApp.currentDirectory != null )
            fc.setDirectory( new File( ModellingApp.currentDirectory ) );
        if ( !fc.showDialog( parent ) )
            return;
        File dir = fc.getDirectory();
        File f = fc.getSelectedFile();
        String name = f.getName();
        String baseName = ( name.endsWith( ".obj" ) ? name.substring( 0, name.length() - 4 ) : name );
        ModellingApp.currentDirectory = dir.getAbsolutePath();

        // Create the output files.

        try
        {
            TextureImageExporter textureExporter = null;
            String mtlFilename = null;
            if ( mtlBox.getState() )
            {
                textureExporter = new TextureImageExporter( dir, baseName, (int) ( 100 * qualitySlider.getValue() ),
                        TextureImageExporter.DIFFUSE + TextureImageExporter.HILIGHT + TextureImageExporter.EMISSIVE,
                        (int) widthField.getValue(), (int) heightField.getValue() );
                mtlFilename = baseName + ".mtl";
                PrintWriter out = new PrintWriter( new BufferedWriter( new FileWriter( new File( dir, mtlFilename ) ) ) );
                writeTextures( theScene, out, false, textureExporter );
                out.close();
                textureExporter.saveImages();
            }
            PrintWriter out = new PrintWriter( new BufferedWriter( new FileWriter( f ) ) );
            writePolyMesh( theScene, out, textureExporter, mtlFilename );
            out.close();
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
            new BStandardDialog( "", new String[]{Translate.text( "errorExportingScene" ), ex.getMessage()}, BStandardDialog.ERROR ).showMessageDialog( parent );
        }
    }


    /**
     *  Write out the scene in OBJ format to the specified PrintWriter. The
     *  other parameters correspond to the options in the dialog box displayed
     *  by exportFile().
     *
     *@param  theScene         Description of the Parameter
     *@param  out              Description of the Parameter
     *@param  textureExporter  Description of the Parameter
     *@param  mtlFilename      Description of the Parameter
     */

    public static void writePolyMesh( Scene theScene, PrintWriter out, TextureImageExporter textureExporter, String mtlFilename )
    {
        RGBColor color;

        // Write the header information.

        out.println( "#Produced by Art of Illusion " + ModellingApp.VERSION + ", PolyMesh Plugin, " + ( new Date() ).toString() );
        if ( mtlFilename != null )
            out.println( "mtllib " + mtlFilename );

        // Write the objects in the scene.

        int numVert = 0;

        // Write the objects in the scene.

        int numNorm = 0;

        // Write the objects in the scene.

        int numTexVert = 0;
        Hashtable groupNames = new Hashtable();
        NumberFormat nf = NumberFormat.getNumberInstance( Locale.US );
        nf.setMaximumFractionDigits( 5 );
        nf.setGroupingUsed( false );
        for ( int i = 0; i < theScene.getNumObjects(); i++ )
        {
            // Get a rendering mesh for the object.

            ObjectInfo info = theScene.getObject( i );
            if ( !info.selected || !( info.object instanceof PolyMesh ) )
                continue;
            PolyMesh mesh = (PolyMesh) info.object;
            if ( mesh == null )
                continue;

            // Select a name for the group.

            String baseName = info.name.replace( ' ', '_' );
            String name = baseName;
            int append = 1;
            while ( groupNames.get( name ) != null )
                name = baseName + "_" + ( append++ );
            groupNames.put( name, "" );

            // Write out the object.

            out.println( "g " + name );
            TextureImageInfo ti = null;
            if ( textureExporter != null )
            {
                ti = textureExporter.getTextureInfo( info.object.getTexture() );
                if ( ti != null )
                    out.println( "usemtl " + ti.name );
            }
            Mat4 trans = info.coords.fromLocal();
            MeshVertex vert[] = mesh.getVertices();
            for ( int j = 0; j < vert.length; j++ )
            {
                Vec3 v = trans.times( vert[j].r );
                out.println( "v " + nf.format( v.x ) + " " + nf.format( v.y ) + " " + nf.format( v.z ) );
            }
            Vec3 norm[] = mesh.getNormals();
            for ( int j = 0; j < norm.length; j++ )
            {
                if ( norm[j] == null )
                    out.println( "vn 1 0 0" );
                else
                {
                    Vec3 v = trans.timesDirection( norm[j] );
                    out.println( "vn " + nf.format( v.x ) + " " + nf.format( v.y ) + " " + nf.format( v.z ) );
                }
            }
            PolyMesh.Wface face[] = mesh.getFaces();
            //no per face vertew texture mapping.
            /*
             *  if (ti != null && mesh.getTextureMapping() instanceof UVMapping && ((UVMapping) mesh.getTextureMapping()).isPerFaceVertex(mesh))
             *  {
             *  / A per-face-vertex texture mapping.
             *  Vec2 coords[][] = ((UVMapping) mesh.getTextureMapping()).findFaceTextureCoordinates(mesh);
             *  double uscale = (ti.maxu == ti.minu ? 1.0 : 1.0/(ti.maxu-ti.minu));
             *  double vscale = (ti.maxv == ti.minv ? 1.0 : 1.0/(ti.maxv-ti.minv));
             *  for (int j = 0; j < face.length; j++)
             *  for (int k = 0; k < 3; k++)
             *  {
             *  double u = (coords[k][j].x-ti.minu)*uscale;
             *  double v = (coords[k][j].y-ti.minv)*vscale;
             *  out.println("vt "+nf.format(u)+" "+nf.format(v));
             *  }
             *  for (int j = 0; j < face.length; j++)
             *  out.println("f "+(face[j].v1+numVert+1)+"/"+((j*3)+numTexVert+1)+"/"+(face[j].v1+numNorm+1)+" "+
             *  (face[j].v2+numVert+1)+"/"+((j*3)+numTexVert+2)+"/"+(face[j].v2+numNorm+1)+" "+
             *  (face[j].v3+numVert+1)+"/"+((j*3)+numTexVert+3)+"/"+(face[j].v3+numNorm+1));
             *  numTexVert += face.length*3;
             *  }
             *  else
             */
            if ( ti != null && mesh.getTextureMapping() instanceof Mapping2D )
            {
                // A per-vertex texture mapping.

                Vec2 coords[] = ( (Mapping2D) mesh.getTextureMapping() ).findTextureCoordinates( mesh );
                double uscale = ( ti.maxu == ti.minu ? 1.0 : 1.0 / ( ti.maxu - ti.minu ) );
                double vscale = ( ti.maxv == ti.minv ? 1.0 : 1.0 / ( ti.maxv - ti.minv ) );
                for ( int j = 0; j < coords.length; j++ )
                {
                    double u = ( coords[j].x - ti.minu ) * uscale;
                    double v = ( coords[j].y - ti.minv ) * vscale;
                    out.println( "vt " + nf.format( u ) + " " + nf.format( v ) );
                }
                for ( int j = 0; j < face.length; j++ )
                {
                    int fv[] = mesh.getFaceVertices( face[j] );
                    out.print( "f " );
                    for ( int k = 0; k < fv.length; ++k )
                        out.print( ( fv[k] + numVert + 1 ) + "/" + ( fv[k] + numTexVert + 1 ) + "/" + ( fv[k] + numNorm + 1 ) + " " );
                    out.println( "" );
                }
                numTexVert += coords.length;
            }
            else
            {
                // No texture coordinates.

                for ( int j = 0; j < face.length; j++ )
                {
                    int fv[] = mesh.getFaceVertices( face[j] );
                    out.print( "f " );
                    for ( int k = 0; k < fv.length; ++k )
                        out.print( ( fv[k] + numVert + 1 ) + "//" + ( fv[k] + numNorm + 1 ) + " " );
                    out.println( "" );
                }
            }
            numVert += vert.length;
            numNorm += norm.length;
        }
    }


    /**
     *  Write out the .mtl file describing the textures.
     *
     *@param  theScene         Description of the Parameter
     *@param  out              Description of the Parameter
     *@param  wholeScene       Description of the Parameter
     *@param  textureExporter  Description of the Parameter
     */

    private static void writeTextures( Scene theScene, PrintWriter out, boolean wholeScene, TextureImageExporter textureExporter )
    {
        // Find all the textures.

        for ( int i = 0; i < theScene.getNumObjects(); i++ )
        {
            ObjectInfo info = theScene.getObject( i );
            if ( !wholeScene && !info.selected )
                continue;
            textureExporter.addObject( info );
        }

        // Write out the .mtl file.

        out.println( "#Produced by Art of Illusion " + ModellingApp.VERSION + ", PolyMesh Plugin, " + ( new Date() ).toString() );
        Enumeration enumerate = textureExporter.getTextures();
        Hashtable names = new Hashtable();
        TextureSpec spec = new TextureSpec();
        NumberFormat nf = NumberFormat.getNumberInstance( Locale.US );
        nf.setMaximumFractionDigits( 5 );
        while ( enumerate.hasMoreElements() )
        {
            TextureImageInfo info = (TextureImageInfo) enumerate.nextElement();

            // Select a name for the texture.

            String baseName = info.texture.getName().replace( ' ', '_' );
            if ( names.get( baseName ) == null )
                info.name = baseName;
            else
            {
                int i = 1;
                while ( names.get( baseName + i ) != null )
                    i++;
                info.name = baseName + i;
            }
            names.put( info.name, info );

            // Write the texture.

            out.println( "newmtl " + info.name );
            info.texture.getAverageSpec( spec, 0.0, info.paramValue );
            if ( info.diffuseFilename == null )
                out.println( "Kd " + nf.format( spec.diffuse.getRed() ) + " " + nf.format( spec.diffuse.getGreen() ) + " " + nf.format( spec.diffuse.getBlue() ) );
            else
            {
                out.println( "Kd 1 1 1" );
                out.println( "map_Kd " + info.diffuseFilename );
            }
            if ( info.hilightFilename == null )
                out.println( "Ks " + nf.format( spec.hilight.getRed() ) + " " + nf.format( spec.hilight.getGreen() ) + " " + nf.format( spec.hilight.getBlue() ) );
            else
            {
                out.println( "Ks 1 1 1" );
                out.println( "map_Ks " + info.hilightFilename );
            }
            if ( info.emissiveFilename == null )
                out.println( "Ka " + nf.format( spec.emissive.getRed() ) + " " + nf.format( spec.emissive.getGreen() ) + " " + nf.format( spec.emissive.getBlue() ) );
            else
            {
                out.println( "Ka 1 1 1" );
                out.println( "map_Ka " + info.emissiveFilename );
            }
            if ( info.hilightFilename == null && spec.hilight.getRed() == 0.0f && spec.hilight.getGreen() == 0.0f && spec.hilight.getBlue() == 0.0f )
                out.println( "illum 1" );
            else
            {
                out.println( "illum 2" );
                out.println( "Ns " + (int) ( ( 1.0 - spec.roughness ) * 128.0 + 1.0 ) );
            }
        }
    }
}
