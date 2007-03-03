/*
 *  Copyright (C) 2007 by Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package artofillusion.polymesh;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.ArrayList;

import artofillusion.MaterialPreviewer;
import artofillusion.TextureParameter;
import artofillusion.math.BoundingBox;
import artofillusion.math.Vec2;
import artofillusion.object.FacetedMesh;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedFace;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedVertex;
import artofillusion.texture.Texture;
import artofillusion.texture.Texture2D;
import artofillusion.texture.TextureMapping;
import artofillusion.texture.UVMapping;
import buoy.event.RepaintEvent;
import buoy.widget.BScrollPane;
import buoy.widget.CustomWidget;

/**
 * This canvas displays several meshes over a bitmap image. The goal is to
 * define UV mapping of meshes as the location of the mesh vertices over the
 * background image. Editing tools allow to move, rotate, resize meshes.
 * 
 * @author Francois Guillet
 * 
 */
public class UVMappingCanvas extends CustomWidget {

    private Dimension size;

    private Dimension oldSize;

    private UnfoldedMesh[] meshes;

    private static int MARGIN = 10; // margin around meshes

    private final static Color unselectedColor = new Color(0, 180, 0);

    private final static Color selectedColor = Color.red;

    private final static Color selectedPinnedColor = new Color(0, 162, 255);

    private boolean[] selected;

    private int currentPiece; // only one piece can be selected for edition

    private UVMappingEditorDialog parent;

    private Point[] verticesPoints;

    private int selectedPiece;

    private UVMappingData.UVMeshMapping mapping;

    private Rectangle dragBoxRect;

    private UVMappingManipulator manipulator;

    private int[] verticesTable;

    private UVMappingData mappingData;

    private Vec2 origin;

    private double scale;

    private double umin, umax, vmin, vmax;

    private Image textureImage;

    private int component;

    private boolean disableImageDisplay;

    private MeshPreviewer preview;

    private int[][] vertIndexes;

    private int[][] vertMeshes;

    private Texture texture;

    private UVMapping texMapping;

    private boolean boldEdges;

    private final static Stroke normal = new BasicStroke();

    private final static Stroke bold = new BasicStroke(2.0f);

    private final static Dimension minSize = new Dimension(512, 512);

    private final static Dimension maxSize = new Dimension(5000, 5000);

    private final static Color pinnedColor = new Color(182, 0, 185);

    private final static Color pinnedSelectedColor = new Color(255, 142, 255);

    public UVMappingCanvas(UVMappingEditorDialog window,
	    UVMappingData mappingData, MeshPreviewer preview, Texture texture,
	    UVMapping texMapping) {
	super();
	parent = window;
	this.preview = preview;
	this.texture = texture;
	this.texMapping = texMapping;
	this.mapping = mappingData.mappings.get(0);
	setBackground(Color.white);
	size = new Dimension(512, 512);
	oldSize = new Dimension(512, 512);
	origin = new Vec2();
	this.mappingData = mappingData;
	meshes = mappingData.getMeshes();
	boldEdges = true;
	addEventLink(RepaintEvent.class, this, "doRepaint");
	if (meshes == null)
	    return;
	selectedPiece = 0;
	component = 0;
	resetMeshLayout();
	createImage();
	setSelectedPiece(0);
	initializeTexCoordsIndex();
	updateTextureCoords();
	// repaint();
    }

    public boolean isBoldEdges() {
	return boldEdges;
    }

    public void setBoldEdges(boolean boldEdges) {
	this.boldEdges = boldEdges;
	repaint();
    }

    /**
         * @return the current mesh mapping
         */
    public UVMappingData.UVMeshMapping getMapping() {
	return mapping;
    }

    /**
         * @param mapping
         *                The mapping to set
         */
    public void setMapping(UVMappingData.UVMeshMapping mapping) {
	this.mapping = mapping;
	resetMeshLayout();
	update();
    }

    /**
         * @return the current texture mapping
         */
    public UVMapping getTexMapping() {
	return texMapping;
    }

    private void update() {
	createImage();

	updateTextureCoords();
	clearSelection();
	repaint();
	preview.render();
    }

    /**
         * @return the current texture
         */
    public Texture getTexture() {
	return texture;
    }

    /**
         * @param texture
         *                The texture to set
         * @param texMapping
         *                the texture mapping to set
         */
    public void setTexture(Texture texture, UVMapping texMapping) {
	this.texture = texture;
	this.texMapping = texMapping;
	update();
    }

    /*
         * (non-Javadoc)
         * 
         * @see buoy.widget.Widget#getPreferredSize()
         */
    @Override
    public Dimension getPreferredSize() {
	Dimension viewSize = ((BScrollPane) getParent()).getComponent()
		.getSize();
	size.width = viewSize.width;
	size.height = viewSize.height;
	if (size.width < minSize.width) {
	    size.width = minSize.width;
	} else if (size.width > maxSize.width) {
	    size.width = maxSize.width;
	}
	if (size.height < minSize.height) {
	    size.height = minSize.height;
	} else if (size.height > maxSize.height) {
	    size.height = maxSize.height;
	}
	return size;
    }

    /*
         * (non-Javadoc)
         * 
         * @see buoy.widget.Widget#getMinimumSize()
         */
    @Override
    public Dimension getMinimumSize() {
	return minSize;
    }

    /*
         * (non-Javadoc)
         * 
         * @see buoy.widget.Widget#getMaximumSize()
         */
    @Override
    public Dimension getMaximumSize() {
	return maxSize;
    }

    /**
         * Draws the mesh pieces
         * 
         */
    @SuppressWarnings("unused")
    private void doRepaint(RepaintEvent evt) {
	Graphics2D g = evt.getGraphics();
	doPaint(g, false);
    }

    private void doPaint(Graphics2D g, boolean export) {
	if (meshes == null)
	    return;
	if (oldSize.width != size.width || oldSize.height != size.height) {
	    vmax = origin.y + (size.height ) / (2 * scale);
	    vmin = origin.y - (size.height ) / (2 * scale);
	    umax = origin.x + (size.width ) / (2 * scale);
	    umin = origin.x - (size.width ) / (2 * scale);
	    parent.displayUVMinMax(umin, umax, vmin, vmax);
	    createImage();
	    refreshVerticesPoints();
	    oldSize = new Dimension(size);
	}
	if (textureImage != null) {
	    g.drawImage(textureImage, 0, 0, null);
	}
	for (int i = 0; i < meshes.length; i++) {
	    UnfoldedMesh mesh = meshes[i];
	    Vec2[] v = mapping.v[i];
	    UnfoldedEdge[] e = mesh.getEdges();
	    Point p1;
	    Point p2;
	    if (export || selectedPiece == i) {
		g.setColor(mapping.edgeColor);
		if (boldEdges) {
		    g.setStroke(bold);
		}
	    } else {
		g.setColor(Color.gray);
		g.setStroke(normal);
	    }
	    for (int j = 0; j < e.length; j++) {
		if (e[j].hidden) {
		    continue;
		}
		p1 = VertexToLayout(v[e[j].v1]);
		p2 = VertexToLayout(v[e[j].v2]);
		g.drawLine(p1.x, p1.y, p2.x, p2.y);
	    }
	}
	g.setStroke(normal);
	if (!export) {
	    for (int i = 0; i < verticesPoints.length; i++) {
		if (selected[i]) {
		    if (mappingData.meshes[currentPiece].vertices[verticesTable[i]].pinned) {
			g.setColor(pinnedSelectedColor);
		    } else {
			g.setColor(selectedColor);
		    }
		    g.drawOval(verticesPoints[i].x - 3,
			    verticesPoints[i].y - 3, 6, 6);
		} else {
		    if (mappingData.meshes[currentPiece].vertices[verticesTable[i]].pinned) {
			g.setColor(pinnedColor);
		    } else {
			g.setColor(unselectedColor);
		    }
		    g.fillOval(verticesPoints[i].x - 3,
			    verticesPoints[i].y - 3, 6, 6);
		}
	    }
	    if (dragBoxRect != null) {
		g.setColor(Color.black);
		g.drawRect(dragBoxRect.x, dragBoxRect.y, dragBoxRect.width,
			dragBoxRect.height);
	    }
	    if (manipulator != null)
		manipulator.paint(g);
	}
    }

    public void resetMeshLayout() {
	vmin = umin = Double.MAX_VALUE;
	vmax = umax = -Double.MAX_VALUE;
	Vec2[] v;
	for (int i = 0; i < meshes.length; i++) {
	    v = mapping.v[i];
	    for (int j = 0; j < v.length; j++) {
		if (v[j].x < umin) {
		    umin = v[j].x;
		} else if (v[j].x > umax) {
		    umax = v[j].x;
		}
		if (v[j].y < vmin) {
		    vmin = v[j].y;
		} else if (v[j].y > vmax) {
		    vmax = v[j].y;
		}
	    }
	}
	umin -= 0.1 * Math.abs(umin);
	vmin -= 0.1 * Math.abs(vmin);
	umax += 0.1 * Math.abs(umax);
	vmax += 0.1 * Math.abs(vmax);
	setRange(umin, umax, vmin, vmax);
    }

    public void setRange(double umin, double umax, double vmin, double vmax) {
	this.umin = umin;
	this.umax = umax;
	this.vmin = vmin;
	this.vmax = vmax;
	scale = ((double) (size.width)) / (umax - umin);
	double scaley = ((double) (size.height)) / (vmax - vmin);
	if (scaley < scale)
	    scale = scaley;
	origin.x = (umax + umin) / 2;
	origin.y = (vmax + vmin) / 2;
	vmax = origin.y + (size.height ) / (2 * scale);
	vmin = origin.y - (size.height ) / (2 * scale);
	umax = origin.x + (size.width ) / (2 * scale);
	umin = origin.x - (size.width ) / (2 * scale);
	createImage();
	refreshVerticesPoints();
	parent.displayUVMinMax(umin, umax, vmin, vmax);
    }

    public void refreshVerticesPoints() {
	UnfoldedVertex[] vert = meshes[selectedPiece].vertices;
	Vec2[] v = mapping.v[selectedPiece];
	int count = 0;
	for (int j = 0; j < v.length; j++) {
	    if (vert[j].id != -1)
		count++;
	}
	verticesPoints = new Point[count];
	verticesTable = new int[count];
	count = 0;
	for (int j = 0; j < v.length; j++) {
	    if (vert[j].id == -1)
		continue;
	    verticesPoints[count] = VertexToLayout(v[j]);
	    verticesTable[count] = j;
	    count++;
	}
    }

    public void updatePreview() {
	Mesh mesh = (Mesh) preview.getObject().object;
	MeshVertex[] vert = mesh.getVertices();
	UnfoldedMesh umesh = meshes[selectedPiece];
	UnfoldedVertex[] uvert = umesh.getVertices();
	boolean[] meshSel = new boolean[vert.length];
	for (int i = 0; i < selected.length; i++) {
	    if (selected[i]) {
		meshSel[uvert[verticesTable[i]].id] = true;
	    }
	}
	preview.setVertexSelection(meshSel);
	preview.render();
    }

    /**
         * @return the vertex selection
         */
    public boolean[] getSelection() {
	return selected;
    }

    public void setSelection(boolean[] selected) {
	setSelection(selected, true);
    }

    /**
         * @param selected
         *                Sets the selected vertices
         */
    public void setSelection(boolean[] selected, boolean render) {

	if (selected.length == verticesPoints.length) {
	    this.selected = selected;
	    if (render) {
		updatePreview();
	    } else {
		preview.clearVertexSelection();
	    }
	    repaint();
	}
    }

    public void clearSelection() {
	for (int i = 0; i < selected.length; i++) {
	    selected[i] = false;
	}
	preview.clearVertexSelection();
    }

    /**
         * @return the selected piece
         */
    public int getSelectedPiece() {
	return selectedPiece;
    }

    /**
         * @param selectedPiece
         *                the piece currently selected
         */
    public void setSelectedPiece(int selectedPiece) {
	this.selectedPiece = selectedPiece;
	refreshVerticesPoints();
	selected = new boolean[verticesPoints.length];
	repaint();
    }

    /**
         * @return the vertices 2D points on canvas (only for displayed
         *         vertices)
         */
    public Point[] getVerticesPoints() {
	return verticesPoints;
    }

    /**
         * Sets the current drag box and repaints the mesh. Set the drag box to
         * null in order to stop the drag box display.
         * 
         * @param dragBoxRect
         *                The drag box to display
         */
    public void setDragBox(Rectangle dragBoxRect) {
	this.dragBoxRect = dragBoxRect;
	repaint();
    }

    /**
         * Sets the manipulator that manipulates mesh pieces
         * 
         * @param manipulator
         */
    public void setManipulator(UVMappingManipulator manipulator) {
	this.manipulator = manipulator;
    }

    /**
         * Sets the positions of vertices relative to view window (not to UV
         * values). If mask is not null, change is applied only for points for
         * which mask is true.
         * 
         * @param newPos
         * @param mask
         */
    public void setPositions(Point[] newPos, boolean[] mask) {
	UnfoldedVertex[] vert = meshes[selectedPiece].vertices;
	Vec2[] v = mapping.v[selectedPiece];
	for (int i = 0; i < newPos.length; i++) {
	    if (mask == null || mask[i]) {
		LayoutToVertex(v[verticesTable[i]], newPos[i]);
	    }
	}

    }

    /**
         * This function returns the rectangle that encloses the mesh, taking
         * into accout mesh origin, orientation and scale
         * 
         * @return mesh bounds
         */
    public BoundingBox getBounds(UnfoldedMesh mesh) {
	int xmin, xmax, ymin, ymax;
	xmax = ymax = Integer.MIN_VALUE;
	xmin = ymin = Integer.MAX_VALUE;
	Point p;
	Vec2[] v = mapping.v[selectedPiece];
	for (int i = 0; i < v.length; i++) {
	    p = VertexToLayout(v[i]);
	    if (xmax < p.x)
		xmax = p.x;
	    if (xmin > p.x)
		xmin = p.x;
	    if (ymax < p.y)
		ymax = p.y;
	    if (ymin > p.y)
		ymin = p.y;
	}
	BoundingBox b = new BoundingBox(xmin, xmax, ymin, ymax, 0, 0);
	return b;
    }

    public Point VertexToLayout(Vec2 r) {
	Point p = new Point();
	p.x = (int) Math.round((r.x - origin.x) * scale);
	p.y = (int) Math.round((r.y - origin.y) * scale);
	p.x += size.width / 2;
	p.y = size.height / 2 - p.y;
	return p;
    }

    /**
         * Computes the position of a vertex given its position on the layout
         * 
         * @param p
         *                The new vertex position
         * @param index
         *                The vertex index
         */
    public void LayoutToVertex(Vec2 v, Point p) {
	v.x = (p.x - size.width / 2) / scale + origin.x;
	v.y = (size.height / 2 - p.y) / scale + origin.y;
    }

    public void setComponent(int component) {
	this.component = component;
	createImage();
	repaint();
    }

    /** Recalculate the texture image. */

    private void createImage() {
	textureImage = null;
	if (disableImageDisplay) {
	    return;
	}
	if (texture == null)
	    return;
	int sampling = mappingData.sampling;
	setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	double uoffset = 0; // 0.5 * sampling * (umax - umin) / size.width;
	double voffset = 0; // 0.5 * sampling * (vmax - vmin) / size.height;
	TextureParameter param[] = texMapping.getParameters();
	double paramVal[] = null;
	if (param != null) {
	    paramVal = new double[param.length];
	    for (int i = 0; i < param.length; i++)
		paramVal[i] = param[i].defaultVal;
	}
	textureImage = ((Texture2D) texture.duplicate()).createComponentImage(
		umin + uoffset, umax + uoffset, vmin - voffset, vmax - voffset,
		size.width / sampling, size.height / sampling, component, 0.0,
		paramVal);
	if (sampling > 1)
	    textureImage = textureImage.getScaledInstance(size.width,
		    size.height, Image.SCALE_SMOOTH);
	setCursor(Cursor.getDefaultCursor());
    }

    public void setSampling(int sampling) {
	mappingData.sampling = sampling;
	createImage();
	repaint();
    }

    public int getSampling() {
	return mappingData.sampling;
    }

    public void scale(double sc) {
	umin = sc * (umin - origin.x) + origin.x;
	umax = sc * (umax - origin.x) + origin.x;
	vmin = sc * (vmin - origin.y) + origin.y;
	vmax = sc * (vmax - origin.y) + origin.y;
	scale /= sc;
	createImage();
	refreshVerticesPoints();
	repaint();
	parent.displayUVMinMax(umin, umax, vmin, vmax);
    }

    public void disableImageDisplay() {
	disableImageDisplay = true;
	textureImage = null;
    }

    public void enableImageDisplay() {
	disableImageDisplay = false;
	createImage();
	repaint();
    }

    /**
         * @return the scale
         */
    public double getScale() {
	return scale;
    }

    /**
         * @param scale
         *                the scale to set
         */
    public void setScale(double sc) {
	double f = scale / sc;
	umin = f * (umin - origin.x) + origin.x;
	umax = f * (umax - origin.x) + origin.x;
	vmin = f * (vmin - origin.y) + origin.y;
	vmax = f * (vmax - origin.y) + origin.y;
	scale = sc;
	createImage();
	refreshVerticesPoints();
	repaint();
	parent.displayUVMinMax(umin, umax, vmin, vmax);
    }

    public void moveOrigin(double du, double dv) {
	umin += du;
	umax += du;
	vmin += dv;
	vmax += dv;
	origin.x += du;
	origin.y += dv;
	createImage();
	refreshVerticesPoints();
	repaint();
	parent.displayUVMinMax(umin, umax, vmin, vmax);
    }

    /**
         * @return the origin
         */
    public Vec2 getOrigin() {
	return new Vec2(origin);
    }

    /**
         * @param origin
         *                the origin to set
         */
    public void setOrigin(Vec2 origin) {
	setOrigin(origin.x, origin.y);
    }

    /**
         * @param origin
         *                the origin to set
         */
    public void setOrigin(double u, double v) {
	moveOrigin(u - origin.x, v - origin.y);
    }

    public void initializeTexCoordsIndex() {
	FacetedMesh mesh = (FacetedMesh) preview.getObject().object;
	int[][] texCoordIndex = new int[mesh.getFaceCount()][];
	vertIndexes = new int[texCoordIndex.length][];
	vertMeshes = new int[texCoordIndex.length][];
	int n;
	for (int i = 0; i < texCoordIndex.length; i++) {
	    n = mesh.getFaceVertexCount(i);
	    texCoordIndex[i] = new int[n];
	    vertIndexes[i] = new int[n];
	    vertMeshes[i] = new int[n];
	    for (int j = 0; j < texCoordIndex[i].length; j++) {
		texCoordIndex[i][j] = mesh.getFaceVertexIndex(i, j);
		vertIndexes[i][j] = -1;
		vertMeshes[i][j] = -1;
	    }
	}
	UnfoldedFace[] f;
	UnfoldedVertex[] v;
	int count = 0;
	for (int i = 0; i < meshes.length; i++) {
	    v = meshes[i].getVertices();
	    count += v.length;
	}
	count = 0;
	// System.out.println("nb de faces " +
	// ((PolyMesh)preview.getObject().object).getFaces().length);
	for (int i = 0; i < meshes.length; i++) {
	    f = meshes[i].getFaces();
	    v = meshes[i].getVertices();
	    for (int j = 0; j < f.length; j++) {
		// System.out.println("face " + j + " " + f[j].id);
		if (f[j].id >= 0 && f[j].id < texCoordIndex.length) {
		    for (int k = 0; k < texCoordIndex[f[j].id].length; k++) {
			if (f[j].v1 >= 0
				&& v[f[j].v1].id == texCoordIndex[f[j].id][k]) {
			    vertIndexes[f[j].id][k] = f[j].v1;
			    vertMeshes[f[j].id][k] = i;
			}
			if (f[j].v2 >= 0
				&& v[f[j].v2].id == texCoordIndex[f[j].id][k]) {
			    vertIndexes[f[j].id][k] = f[j].v2;
			    vertMeshes[f[j].id][k] = i;
			}
			if (f[j].v3 >= 0
				&& v[f[j].v3].id == texCoordIndex[f[j].id][k]) {
			    vertIndexes[f[j].id][k] = f[j].v3;
			    vertMeshes[f[j].id][k] = i;
			}
		    }
		}
		// else {
		// System.out.println("rejetée " + j + " " + f[j].id);
		// }
	    }
	    count += v.length;
	}
    }

    public void updateTextureCoords() {

	if (texture == null)
	    return;
	FacetedMesh mesh = (FacetedMesh) preview.getObject().object;
	Vec2 texCoord[][] = new Vec2[mesh.getFaceCount()][];
	for (int i = 0; i < texCoord.length; i++) {
	    texCoord[i] = new Vec2[mesh.getFaceVertexCount(i)];
	    for (int j = 0; j < texCoord[i].length; j++) {
		texCoord[i][j] = new Vec2(
			mapping.v[vertMeshes[i][j]][vertIndexes[i][j]]);
	    }
	}
	texMapping.setFaceTextureCoordinates(preview.getObject().object,
		texCoord);
	preview.render();
    }

    public void selectAll() {
	for (int i = 0; i < verticesPoints.length; i++) {
	    selected[i] = true;
	}
	setSelection(selected);
	manipulator.selectionUpdated();
    }

    public UnfoldedMesh[] getMeshes() {
	return meshes;
    }

    public void drawOnto(Graphics2D g, int width, int height) {
	g.setColor(Color.white);
	g.fillRect(0, 0, width, height);
	g.setColor(Color.black);
	double oldScale = scale;
	double oldUmin = umin;
	double oldUmax = umax;
	double oldVmin = vmin;
	double oldVmax = vmax;
	Vec2 oldOrigin = new Vec2(origin);
	Dimension tmpSize = size;
	Dimension tmpOldSize = oldSize;
	oldSize = size = new Dimension(width, height);
	Image oldTextureImage = textureImage;

	textureImage = null;
	scale = ((double) (width)) / (umax - umin);
	double scaley = ((double) (height)) / (vmax - vmin);
	if (scaley < scale)
	    scale = scaley;
	origin.x = (umax + umin) / 2;
	origin.y = (vmax + vmin) / 2;
	vmax = origin.y + height / (2 * scale);
	vmin = origin.y - height / (2 * scale);
	umax = origin.x + width / (2 * scale);
	umin = origin.x - width / (2 * scale);
	refreshVerticesPoints();
	doPaint(g, true);
	textureImage = oldTextureImage;
	scale = oldScale;
	umin = oldUmin;
	umax = oldUmax;
	vmin = oldVmin;
	vmax = oldVmax;
	origin = new Vec2(oldOrigin);
	size = tmpSize;
	oldSize = tmpOldSize;
	refreshVerticesPoints();
    }

    public void pinSelection(boolean state) {
	for (int i = 0; i < verticesPoints.length; i++) {
	    if (selected[i]) {
		mappingData.meshes[currentPiece].vertices[verticesTable[i]].pinned = state;
	    }
	}
	repaint();
    }
    
    public boolean isPinned(int i) {
	return mappingData.meshes[currentPiece].vertices[verticesTable[i]].pinned;
    }
}
