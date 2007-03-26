/*
 *  Copyright (C) 1999-2004 by Peter Eastman (TriMeshViewer.java),
 *  Modifications for Winged Edge Mesh Copyright (C) 2004-2005 by François Guillet
 *
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;

import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.RenderingMesh;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.animation.SkeletonTool;
import artofillusion.math.RGBColor;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.MeshVertex;
import artofillusion.object.ObjectInfo;
import artofillusion.polymesh.PolyMesh.Wedge;
import artofillusion.polymesh.PolyMesh.Wface;
import artofillusion.polymesh.PolyMesh.Wvertex;
import artofillusion.texture.FaceParameterValue;
import artofillusion.ui.EditingTool;
import artofillusion.ui.MeshEditController;
import artofillusion.view.ConstantVertexShader;
import artofillusion.view.FlatVertexShader;
import artofillusion.view.SelectionVertexShader;
import artofillusion.view.SmoothVertexShader;
import artofillusion.view.TexturedVertexShader;
import artofillusion.view.VertexShader;
import buoy.event.KeyPressedEvent;
import buoy.event.MouseClickedEvent;
import buoy.event.MouseMovedEvent;
import buoy.event.WidgetEvent;
import buoy.event.WidgetMouseEvent;
import buoy.widget.RowContainer;

/**
 * The PolyMeshViewer class is a component which displays a PolyMesh object and
 * allow the user to edit it.
 * 
 * @author Francois Guillet
 * @created december, 22 2004
 */

public class PolyMeshViewer extends MeshViewer {
	private boolean draggingSelectionBox, dragging;

	private int deselect;

	private Point screenVert[];

	private Point screenVertNormals[];

	private double screenZ[];

	private double screenZNormals[];

	boolean visible[];

	private ArrayList manipulators;

	/**
	 * Constructor for the PolyMeshViewer object
	 * 
	 * @param p
	 *            Parent RowContainer
	 * @param window
	 *            MeshEditController
	 */
	public PolyMeshViewer(MeshEditController window, RowContainer p) {
		super(window, p);
		PolyMesh mesh = (PolyMesh) window.getObject().object;
		visible = new boolean[mesh.getVertices().length];
		manipulators = new ArrayList();
		addEventLink(MouseMovedEvent.class, this, "mouseMoved");
		addEventLink(MouseClickedEvent.class, this, "mouseClicked");
	}

	/**
	 * Adds a manipulator to the current list of manipulators
	 * 
	 * @param manipulator
	 *            The manipulator to add to the current set
	 */
	public void addManipulator(Manipulator manipulator) {
		manipulators.add(manipulator);
	}

	/**
	 * Sets the manipulator set to one manipulator. The current manipulator list
	 * is cleared in the process
	 * 
	 * @param manipulator
	 *            The manipulator to initialize the manipulator set to
	 */
	public void setManipulator(Manipulator manipulator) {
		manipulators.clear();
		manipulators.add(manipulator);
	}

	public ArrayList getManipulators() {
		return manipulators;
	}

	public void removeManipulator(Manipulator manipulator) {
		manipulators.remove(manipulator);
	}

	/**
	 * Mesh drawing
	 * 
	 */
	protected void drawObject() {
		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		PolyMesh viewMesh = mesh;
		boolean mirror = false;
		if (mesh.getMirrorState() != PolyMesh.NO_MIRROR) {
			viewMesh = mesh.getMirroredMesh();
			mirror = true;
		}
		PolyMesh subMesh = viewMesh;
		int[] invVertTable = mesh.getInvMirroredVerts();
		Wvertex v[] = (Wvertex[]) viewMesh.getVertices();
		Vec2 p[];

		// Calculate the screen coordinates of every vertex.

		int length = v.length;
		if (mirror)
			length = invVertTable.length;
		screenVert = new Point[length];
		screenZ = new double[length];
		if (visible.length != length)
			visible = new boolean[length];
		p = new Vec2[v.length];
		double clipDist = (theCamera.isPerspective() ? theCamera
				.getClipDistance() : -Double.MAX_VALUE);
		boolean hideVert[] = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller).hideVert
				: new boolean[v.length]);
		boolean project = (controller instanceof PolyMeshEditorWindow && ((PolyMeshEditorWindow) controller)
				.getProjectOntoSurface());
		if (project && viewMesh.getSubdividedMesh() != null)
			subMesh = viewMesh.getSubdividedMesh();
		Wvertex sv[] = (Wvertex[]) subMesh.getVertices();
		screenVertNormals = null;
		screenZNormals = null;
		if (controller instanceof PolyMeshEditorWindow
				&& ((PolyMeshEditorWindow) controller).showNormals()) {
			screenVertNormals = new Point[length];
			screenZNormals = new double[length];
		}
		Vec2 npt;
		for (int i = 0; i < length; i++) {
			Vec3 pos = sv[i].r;
			p[i] = theCamera.getObjectToScreen().timesXY(pos);
			screenVert[i] = new Point((int) p[i].x, (int) p[i].y);
			screenZ[i] = theCamera.getObjectToView().timesZ(pos);
			if (screenVertNormals != null && sv[i].normal != null) {
				pos = sv[i].r.plus(sv[i].normal.times(0.5));
				npt = theCamera.getObjectToScreen().timesXY(pos);
				screenVertNormals[i] = new Point((int) npt.x, (int) npt.y);
				screenZNormals[i] = theCamera.getObjectToView().timesZ(pos);
			}
			if (mirror) {
				visible[i] = (!hideVert[invVertTable[i]] && screenZ[i] > clipDist);
			} else
				visible[i] = (!hideVert[i] && screenZ[i] > clipDist);

		}

		// Now draw the object.
		drawSurface();
		if (!(currentTool instanceof SkeletonTool)) {
			if (showSkeleton && mesh.getSkeleton() != null)
				mesh.getSkeleton().draw(this, false);
		}
		if (controller.getSelectionMode() == MeshEditController.POINT_MODE) {
			drawEdges(p);
			drawVertices();
		} else {
			drawEdges(p);
		}
		if (currentTool instanceof SkeletonTool)
			if (showSkeleton && mesh.getSkeleton() != null)
				mesh.getSkeleton().draw(this, true);
		// Now draw manipulators
		for (int i = 0; i < manipulators.size(); i++)
			((Manipulator) manipulators.get(i)).draw();
	}

	/**
	 * Draw the surface of the object.
	 */

	private void drawSurface() {
		if (!showSurface)
			return;
		boolean hide[] = null;
		int faceIndex[] = null;
		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		Color surfaceColor = mesh.getMeshColor();
		RGBColor surfaceRGBColor = mesh.getMeshRGBColor();
		ObjectInfo objInfo = controller.getObject();
		if (controller instanceof PolyMeshEditorWindow
				&& ((PolyMeshEditorWindow) controller).getExtraParameter() != null) {
			RenderingMesh rmesh = objInfo.getPreviewMesh();
			double param[] = ((FaceParameterValue) rmesh.param[rmesh.param.length - 1])
					.getValue();
			faceIndex = new int[param.length];
			for (int i = 0; i < faceIndex.length; i++)
				faceIndex[i] = (int) param[i];
			boolean hideFace[] = ((PolyMeshEditorWindow) controller).hideFace;
			if (hideFace != null) {
				hide = new boolean[param.length];
				for (int i = 0; i < hide.length; i++)
					hide[i] = hideFace[faceIndex[i]];
			}
		}
		if (renderMode == RENDER_WIREFRAME) {
			renderWireframe(objInfo.getWireframePreview(), theCamera,
					surfaceColor);
		} else if (renderMode == RENDER_TRANSPARENT) {
			VertexShader shader = new ConstantVertexShader(mesh
					.getMeshRGBColor());
			if (faceIndex != null
					&& controller.getSelectionMode() == MeshEditController.FACE_MODE) {
				shader = new SelectionVertexShader(mesh
						.getSelectedFaceRGBColor(), shader, faceIndex, controller
						.getSelection());
			}
			renderMeshTransparent(objInfo.getPreviewMesh(), shader, theCamera,
					theCamera.getViewToWorld().timesDirection(Vec3.vz()), hide);
		} else {
			RenderingMesh rmesh = objInfo.getPreviewMesh();
			Vec3 viewDir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
			VertexShader shader;
			if (renderMode == RENDER_FLAT)
				shader = new FlatVertexShader(rmesh, surfaceRGBColor, viewDir);
			else if (renderMode == RENDER_SMOOTH)
				shader = new SmoothVertexShader(rmesh, surfaceRGBColor, viewDir);
			else
				shader = new TexturedVertexShader(rmesh, objInfo.object, 0.0,
						viewDir).optimize();
			if (faceIndex != null
					&& controller.getSelectionMode() == MeshEditController.FACE_MODE)
				shader = new SelectionVertexShader(mesh
						.getSelectedFaceRGBColor(),
						shader, faceIndex, controller.getSelection());
			renderMesh(rmesh, shader, theCamera, objInfo.object.isClosed(),
					hide);
		}
	}

	/**
	 * Draw the vertices of the control mesh.
	 * 
	 * @param unselectedColor
	 *            Color for unselected vertices
	 * @param selectedColor
	 *            Color for selected vertices
	 */

	private void drawVertices() {
		if (!showMesh)
			return;
		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		PolyMesh viewMesh = mesh;
		Color vertColor = mesh.getVertColor();
		Color selectedVertColor = vertColor;
		int handleSize = mesh.getHandleSize();
		if (currentTool.hilightSelection()) {
			selectedVertColor = mesh.getSelectedVertColor();
		}
		if (currentTool instanceof SkeletonTool) {
			selectedVertColor = disableColor(selectedVertColor);
			vertColor = disableColor(vertColor);
		}
		// First, draw any unselected portions of the object.
		boolean selected[] = controller.getSelection();
		int ref = 0;
		boolean mirror = false;
		int[] invVertTable = mesh.getInvMirroredVerts();
		;
		if (mesh.getMirrorState() != PolyMesh.NO_MIRROR) {
			mirror = true;
			viewMesh = mesh.getMirroredMesh();
		}
		PolyMesh subMesh = viewMesh;
		boolean project = (controller instanceof PolyMeshEditorWindow && ((PolyMeshEditorWindow) controller)
				.getProjectOntoSurface());
		if (project && viewMesh.getSubdividedMesh() != null)
			subMesh = subMesh.getSubdividedMesh();
		Wvertex sv[] = (Wvertex[]) subMesh.getVertices();
		for (int i = 0; i < screenVert.length; i++) {
			if (mirror)
				ref = invVertTable[i];
			else
				ref = i;
			if (renderMode == RENDER_WIREFRAME
					|| renderMode == RENDER_TRANSPARENT) {
				if (!selected[ref] && visible[i])
					drawBox(screenVert[i].x - handleSize / 2, screenVert[i].y
							- handleSize / 2, handleSize, handleSize,
							vertColor);
			} else {
				if (!selected[ref] && visible[i])
					renderBox(screenVert[i].x - handleSize / 2,
							screenVert[i].y - handleSize / 2, handleSize,
							handleSize, screenZ[i] - 0.01, vertColor);
			}

			// Now draw the selected portions.

			if (renderMode == RENDER_WIREFRAME
					|| renderMode == RENDER_TRANSPARENT) {
				if (selected[ref] && visible[i])
					drawBox(screenVert[i].x - handleSize / 2, screenVert[i].y
							- handleSize / 2, handleSize, handleSize,
							selectedVertColor);
			} else {
				if (selected[ref] && visible[i])
					renderBox(screenVert[i].x - handleSize / 2,
							screenVert[i].y - handleSize / 2, handleSize,
							handleSize, screenZ[i] - 0.01, selectedVertColor);
			}
			if (screenVertNormals != null && screenVertNormals[i] != null) {
				if (renderMode == RENDER_WIREFRAME
						|| renderMode == RENDER_TRANSPARENT) {
					if (!selected[ref] && visible[i]) {
						drawLine(screenVert[i], screenVertNormals[i],
								Color.black);
						drawBox(screenVertNormals[i].x - handleSize / 2,
								screenVertNormals[i].y - handleSize / 2,
								handleSize, handleSize, vertColor);
					}
				} else {
					if (!selected[ref] && visible[i]) {
						renderLine(sv[i].r, sv[i].r.plus(sv[i].normal
								.times(0.5)), theCamera, Color.black);
						renderBox(screenVertNormals[i].x - handleSize / 2,
								screenVertNormals[i].y - handleSize / 2,
								handleSize, handleSize,
								screenZNormals[i] - 0.01, vertColor);
					}
				}

				// Now draw the selected portions.

				if (renderMode == RENDER_WIREFRAME
						|| renderMode == RENDER_TRANSPARENT) {
					if (selected[ref] && visible[i]) {
						drawLine(screenVert[i], screenVertNormals[i],
								Color.black);
						drawBox(screenVertNormals[i].x - handleSize / 2,
								screenVertNormals[i].y - handleSize / 2,
								handleSize, handleSize, selectedVertColor);
					}
				} else {
					if (selected[ref] && visible[i]) {
						renderLine(sv[i].r, sv[i].r.plus(sv[i].normal
								.times(0.5)), theCamera, Color.black);
						renderBox(screenVertNormals[i].x - handleSize / 2,
								screenVertNormals[i].y - handleSize / 2,
								handleSize, handleSize,
								screenZNormals[i] - 0.01, selectedVertColor);
					}
				}
			}
		}
	}

	/**
	 * Draw the edges of the control mesh.
	 * 
	 * @param p
	 *            Description of the Parameter
	 * @param unselectedColor
	 *            Color for unselected vertices
	 * @param selectedColor
	 *            Color for selected vertices
	 */

	private void drawEdges(Vec2[] p) {
		if (!showMesh)
			return;
		PolyMesh divMesh = null;
		MeshVertex divVert[] = null;
		Wedge divEdge[] = null;
		Point divScreenVert[] = null;
		double divScreenZ[] = null;
		Vec2 divPos[] = null;

		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		Color seamColor = mesh.getSeamColor();
		Color selectedSeamColor = seamColor;
		Color edgeColor = mesh.getEdgeColor();
		Color selectedEdgeColor = edgeColor;
		if (currentTool.hilightSelection()
				&& !(currentTool instanceof SkeletonTool)) {
			selectedEdgeColor = mesh.getSelectedEdgeColor();
			selectedSeamColor = mesh.getSelectedSeamColor();
		}
		if (currentTool instanceof SkeletonTool) {
			selectedEdgeColor = edgeColor = disableColor(edgeColor);
			selectedSeamColor = seamColor = disableColor(seamColor);
		}
		boolean[] seams = mesh.getSeams();
		boolean seam;
		boolean[] selected = controller.getSelection();
		PolyMesh viewMesh = mesh;

		// First, draw any unselected portions of the object.
		int ref = 0;
		boolean mirror = false;
		int[] invEdgeTable = mesh.invMirroredEdges;
		if (mesh.getMirrorState() != PolyMesh.NO_MIRROR) {
			mirror = true;
			viewMesh = mesh.getMirroredMesh();
		}
		Wedge[] e = viewMesh.getEdges();
		Wedge[] trueEdges = mesh.getEdges();
		Wface[] trueFaces = mesh.getFaces();
		int projectedEdge[] = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller)
				.findProjectedEdges()
				: null);
		if (projectedEdge != null) {
			divMesh = viewMesh.getSubdividedMesh();
			divVert = divMesh.getVertices();
			divEdge = divMesh.getEdges();
			divScreenVert = new Point[divVert.length];
			divScreenZ = new double[divVert.length];
			divPos = new Vec2[divVert.length];

			for (int i = 0; i < divVert.length; i++) {
				divPos[i] = theCamera.getObjectToScreen().timesXY(divVert[i].r);
				divScreenVert[i] = new Point((int) divPos[i].x,
						(int) divPos[i].y);
				divScreenZ[i] = theCamera.getObjectToView()
						.timesZ(divVert[i].r);
			}
		}
		int selectionMode = controller.getSelectionMode();
		boolean[] edgeSelected = selected;

		if (selectionMode == MeshEditController.FACE_MODE) {
			edgeSelected = new boolean[trueEdges.length / 2];
			for (int i = 0; i < trueFaces.length; i++) {

				int fe[] = mesh.getFaceEdges(trueFaces[i]);
				if (selected[i])
					for (int j = 0; j < fe.length; j++) {
						int k = fe[j];
						if (k >= trueEdges.length / 2)
							k = trueEdges[k].hedge;
						edgeSelected[k] = true;
					}
			}
		}
		int index;
		int v1, v2; // orv1, orv2;
		int loop = e.length / 2;
		if (mirror)
			loop = invEdgeTable.length;
		if (projectedEdge != null)
			loop = divEdge.length / 2;
		for (int i = 0; i < loop; i++) {
			if (projectedEdge != null)
				index = projectedEdge[i];
			else
				index = i;
			if (index == -1)
				continue;
			if (mirror && index >= invEdgeTable.length)
				continue;
			if (mirror) {
				ref = invEdgeTable[index];
				v1 = e[index].vertex;
				v2 = e[e[index].hedge].vertex;
			} else {
				ref = index;
				v1 = e[ref].vertex;
				v2 = e[e[ref].hedge].vertex;
			}

			if (renderMode == RENDER_WIREFRAME
					|| renderMode == RENDER_TRANSPARENT) {
				if (selectionMode == MeshEditController.POINT_MODE) {
					if (projectedEdge == null) {
						if (visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							drawLine(screenVert[v1], screenVert[v2],
									seam ? seamColor : edgeColor);
						}
					} else {
						if (visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							drawLine(
									divScreenVert[divEdge[i].vertex],
									divScreenVert[divEdge[divEdge[i].hedge].vertex],
									seam ? seamColor : edgeColor);
						}
					}
				} else if (controller.getSelectionMode() == MeshEditController.EDGE_MODE
						|| controller.getSelectionMode() == MeshEditController.FACE_MODE) {
					if (projectedEdge == null) {
						if (!edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							drawLine(screenVert[v1], screenVert[v2],
									seam ? seamColor : edgeColor);
						}
					} else {
						if (!edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							drawLine(
									divScreenVert[divEdge[i].vertex],
									divScreenVert[divEdge[divEdge[i].hedge].vertex],
									seam ? seamColor : edgeColor);
						}
					}
				}
			} else {
				if (controller.getSelectionMode() == MeshEditController.POINT_MODE) {
					if (projectedEdge == null) {
						if (visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							renderLine(p[v1], screenZ[v1] - 0.01, p[v2],
									screenZ[v2] - 0.01, theCamera,
									seam ? seamColor : edgeColor);
						}
					} else {
						if (visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							renderLine(
									divPos[divEdge[i].vertex],
									divScreenZ[divEdge[i].vertex] - 0.01,
									divPos[divEdge[divEdge[i].hedge].vertex],
									divScreenZ[divEdge[divEdge[i].hedge].vertex] - 0.01,
									theCamera, seam ? seamColor : edgeColor);
						}
					}
				} else if (controller.getSelectionMode() == MeshEditController.EDGE_MODE
						|| controller.getSelectionMode() == MeshEditController.FACE_MODE) {
					if (projectedEdge == null) {
						if (!edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							// System.out.println("seam not selected : " + ref +
							// " / " + seam );
							renderLine(p[v1], screenZ[v1] - 0.01, p[v2],
									screenZ[v2] - 0.01, theCamera,
									seam ? seamColor : edgeColor);
						}
					} else {
						if (!edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							renderLine(
									divPos[divEdge[i].vertex],
									divScreenZ[divEdge[i].vertex] - 0.01,
									divPos[divEdge[divEdge[i].hedge].vertex],
									divScreenZ[divEdge[divEdge[i].hedge].vertex] - 0.01,
									theCamera, seam ? seamColor : edgeColor);
						}
					}
				}
			}

			// Now draw the selected portions.

			if (controller.getSelectionMode() == MeshEditController.EDGE_MODE
					|| controller.getSelectionMode() == MeshEditController.FACE_MODE) {
				if (renderMode == RENDER_WIREFRAME
						|| renderMode == RENDER_TRANSPARENT) {

					if (projectedEdge == null) {
						if (edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							// System.out.println("seam selected : " + ref + " /
							// " + seam );
							drawLine(screenVert[v1], screenVert[v2],
									seam ? selectedSeamColor
											: selectedEdgeColor);
						}
					} else {
						if (edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							drawLine(
									divScreenVert[divEdge[i].vertex],
									divScreenVert[divEdge[divEdge[i].hedge].vertex],
									seam ? selectedSeamColor
											: selectedEdgeColor);
						}
					}
				} else {
					if (projectedEdge == null) {
						if (edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							renderLine(p[v1], screenZ[v1] - 0.01, p[v2],
									screenZ[v2] - 0.01, theCamera,
									seam ? selectedSeamColor
											: selectedEdgeColor);
						}
					} else {
						if (edgeSelected[ref] && visible[v1] && visible[v2]) {
							if (seams != null)
								seam = seams[ref];
							else
								seam = false;
							renderLine(
									divPos[divEdge[i].vertex],
									divScreenZ[divEdge[i].vertex] - 0.01,
									divPos[divEdge[divEdge[i].hedge].vertex],
									divScreenZ[divEdge[divEdge[i].hedge].vertex] - 0.01,
									theCamera, seam ? selectedSeamColor
											: selectedEdgeColor);
						}
					}
				}
			}
		}
	}

	/**
	 * Forwards mouse moved events to current tool if appropriate
	 * 
	 * @param e
	 */
	protected void mouseMoved(WidgetMouseEvent e) {
		activeTool = currentTool;
		if (activeTool instanceof AdvancedEditingTool) {
			Iterator iter = manipulators.iterator();
			while (iter.hasNext()) {
				if (((Manipulator) iter.next()).mouseMoved(e))
					return;
			}
		}
	}

	/**
	 * When the user presses the mouse, forward events to the current tool as
	 * appropriate. If this is a vertex based tool, allow them to select or
	 * deselect vertices.
	 * 
	 * @param e
	 *            Description of the Parameter
	 */

	protected void mousePressed(WidgetMouseEvent e) {
		if (e.isAltDown() && e.getButton() == MouseEvent.BUTTON3) {
			((PolyMeshEditorWindow) getController()).triggerPopupEvent(e);
			return;
		}

		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		MeshVertex v[] = (MeshVertex[]) mesh.getVertices();
		Wedge ed[] = mesh.getEdges();
		Wface f[] = mesh.getFaces();
		int i;
		int j;
		int k;

		requestFocus();
		sentClick = true;
		deselect = -1;
		dragging = false;
		clickPoint = e.getPoint();

		boolean mirror = false;
		if (mesh.getMirrorState() != PolyMesh.NO_MIRROR)
			mirror = true;

		activeTool = currentTool;
		if (!(activeTool instanceof AdvancedEditingTool)) {
			// Determine which tool is active.

			if (metaTool != null && e.isMetaDown())
				activeTool = metaTool;
			else if (altTool != null && e.isAltDown())
				activeTool = altTool;

			// If the current tool wants all clicks, just forward the event and
			// return.
			if (activeTool.whichClicks() == EditingTool.ALL_CLICKS) {
				activeTool.mousePressed(e, this);
				dragging = true;
				return;
			}
		} else {
			Iterator iter = manipulators.iterator();
			if (e.getButton() != MouseEvent.BUTTON3)
				while (iter.hasNext()) {
					if (((Manipulator) iter.next()).mousePressed(e)) {
						dragging = true;
						return;
					}
				}
			// forward to alternate tools
			// Determine which tool is active.
			if (metaTool != null && e.isMetaDown())
				activeTool = metaTool;
			else if (altTool != null && e.isAltDown())
				activeTool = altTool;
			// If the current tool wants all clicks, just forward the event and
			// return.
			if (activeTool != currentTool
					&& activeTool.whichClicks() == EditingTool.ALL_CLICKS) {
				activeTool.mousePressed(e, this);
				dragging = true;
				return;
			}
		}

		// Determine what the click was on.

		i = findClickTarget(e.getPoint(), null);
		// If the click was not on an object, start dragging a selection box.

		if (i == -1) {
			draggingSelectionBox = true;
			beginDraggingSelection(e.getPoint(), false);
			sentClick = false;
			return;
		}

		// If we are in edge or face selection mode, find a vertex of the
		// clicked edge or face,
		// so that it can be passed to editing tools.

		if (controller.getSelectionMode() == MeshEditController.EDGE_MODE) {
			if (mirror) {
				if (visible[mesh.mirroredVerts[ed[i].vertex]])
					j = ed[i].vertex;
				else
					j = ed[ed[i].hedge].vertex;
			} else {
				if (visible[ed[i].vertex])
					j = ed[i].vertex;
				else
					j = ed[ed[i].hedge].vertex;
			}
		} else if (controller.getSelectionMode() == MeshEditController.FACE_MODE) {
			j = i;
			int[] vf = mesh.getFaceVertices(f[i]);
			if (mirror) {
				for (int l = 0; l < vf.length; ++l)
					if (mesh.mirroredVerts[vf[l]] != -1
							&& visible[mesh.mirroredVerts[vf[l]]])
						j = vf[l];
			} else {
				for (int l = 0; l < vf.length; ++l)
					if (visible[vf[l]])
						j = vf[l];
			}
		} else
			j = i;

		// If the click was on a selected object, forward it to the current
		// tool. If it was a
		// shift-click, the user may want to deselect it, so set a flag.

		boolean selected[] = controller.getSelection();

		if (selected[i]) {
			Vec3 pos = null;
			switch (controller.getSelectionMode()) {
			case MeshEditController.POINT_MODE:
				pos = v[i].r;
				break;
			case MeshEditController.EDGE_MODE:
				pos = mesh.getEdgePosition(i);
				break;
			case MeshEditController.FACE_MODE:
				pos = mesh.getFacePosition(i);
				break;
			}
			if (e.isShiftDown())
				deselect = i;
			Iterator iter = manipulators.iterator();
			while (iter.hasNext()) {
				if (((Manipulator) iter.next()).mousePressedOnHandle(e, j, pos))
					return;
			}
			if (!(activeTool instanceof AdvancedEditingTool))
				activeTool.mousePressedOnHandle(e, this, 0, j);
			return;
		}

		// The click was on an unselected object. Select it and send an event to
		// the current tool.

		boolean oldSelection[] = (boolean[]) selected.clone();
		if (!e.isShiftDown()) {
			for (k = 0; k < selected.length; k++)
				selected[k] = false;
		}
		selected[i] = true;
		currentTool.getWindow().setUndoRecord(
				new UndoRecord(currentTool.getWindow(), false,
						UndoRecord.SET_MESH_SELECTION, new Object[] {
								controller,
								new Integer(controller.getSelectionMode()),
								oldSelection }));
		controller.setSelection(selected);
		currentTool.getWindow().updateMenus();
		if (e.isShiftDown())
			sentClick = false;
		else {
			Vec3 pos = null;
			switch (controller.getSelectionMode()) {
			case MeshEditController.POINT_MODE:
				pos = v[i].r;
				break;
			case MeshEditController.EDGE_MODE:
				pos = mesh.getEdgePosition(i);
				break;
			case MeshEditController.FACE_MODE:
				pos = mesh.getFacePosition(i);
				break;
			}
			Iterator iter = manipulators.iterator();
			while (iter.hasNext()) {
				if (((Manipulator) iter.next()).mousePressedOnHandle(e, j, pos))
					return;
			}
			if (!(activeTool instanceof AdvancedEditingTool))
				activeTool.mousePressedOnHandle(e, this, 0, j);
		}
	}

	protected void mouseClicked(WidgetMouseEvent ev) {
		Iterator iter = manipulators.iterator();
		while (iter.hasNext()) {
			if (((Manipulator) iter.next()).mouseClicked(ev))
				break;
		}
	}

	/**
	 * Description of the Method
	 * 
	 * @param e
	 *            Description of the Parameter
	 */
	protected void mouseDragged(WidgetMouseEvent e) {
		if (!dragging && clickPoint == null)
			return;
		Iterator iter = manipulators.iterator();
		while (iter.hasNext()) {
			if (((Manipulator) iter.next()).mouseDragged(e))
				return;
		}
		if (!dragging) {
			Point p = e.getPoint();
			if (Math.abs(p.x - clickPoint.x) < 2
					&& Math.abs(p.y - clickPoint.y) < 2)
				return;
		}
		dragging = true;
		deselect = -1;
		super.mouseDragged(e);
	}

	/**
	 * Description of the Method
	 * 
	 * @param e
	 *            Description of the Parameter
	 */
	protected void mouseReleased(WidgetMouseEvent e) {

		if (e.isAltDown() && e.getButton() == MouseEvent.BUTTON3) {
			((PolyMeshEditorWindow) getController()).triggerPopupEvent(e);
			return;
		}
		PolyMesh mesh = (PolyMesh) getController().getObject().object;
		boolean selected[] = controller.getSelection();
		PolyMesh viewMesh = mesh;
		int ref = 0;
		boolean mirror = false;
		int[] vertTable = mesh.mirroredVerts;
		int[] edgeTable = mesh.mirroredEdges;
		int[] faceTable = mesh.mirroredFaces;
		if (mesh.getMirrorState() != PolyMesh.NO_MIRROR) {
			mirror = true;
			viewMesh = mesh.getMirroredMesh();
		}
		Wface[] trueFaces = mesh.getFaces();
		Wedge ed[] = viewMesh.getEdges();
		Wface fc[] = viewMesh.getFaces();
		int i;
		int j;

		if (!(activeTool instanceof AdvancedEditingTool))
			moveToGrid(e);
		endDraggingSelection();
		boolean oldSelection[] = (boolean[]) selected.clone();
		boolean tolerant = (controller instanceof PolyMeshEditorWindow && ((PolyMeshEditorWindow) controller).tolerant);
		boolean hideFace[] = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller).hideFace
				: new boolean[fc.length]);
		boolean hideVert[] = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller).hideVert
				: new boolean[mesh.getVertices().length]);

		if (draggingSelectionBox && !e.isShiftDown() && !e.isControlDown()) {
			for (i = 0; i < selected.length; i++)
				selected[i] = false;
		}

		// If the user was dragging a selection box, then select or deselect
		// anything
		// it intersects.
		if (selectBounds != null) {
			boolean newsel = !e.isControlDown();
			if (controller.getSelectionMode() == MeshEditController.POINT_MODE) {
				for (i = 0; i < selected.length; i++) {
					if (mirror)
						ref = vertTable[i];
					else
						ref = i;
					if (ref != -1 && !hideVert[i]
							&& selectionRegionContains(screenVert[ref]))
						selected[i] = newsel;
				}
			} else if (controller.getSelectionMode() == MeshEditController.EDGE_MODE) {
				if (tolerant) {
					for (i = 0; i < selected.length; i++) {
						if (mirror)
							ref = edgeTable[i];
						else
							ref = i;
						if (ref != -1
								&& selectionRegionIntersects(
										screenVert[ed[ref].vertex],
										screenVert[ed[ed[ref].hedge].vertex]))
							selected[i] = newsel;
					}
				} else {
					for (i = 0; i < selected.length; i++) {
						if (mirror)
							ref = edgeTable[i];
						else
							ref = i;
						if (ref != -1
								&& selectionRegionContains(screenVert[ed[ref].vertex])
								&& selectionRegionContains(screenVert[ed[ed[ref].hedge].vertex]))
							selected[i] = newsel;
					}
				}
			} else {

				if (tolerant) {
					for (i = 0; i < trueFaces.length; i++)
						if (hideFace == null || !hideFace[i]) {
							if (mirror)
								ref = faceTable[i];
							else
								ref = i;
							if (ref < 0)
								continue;
							int[] vf = viewMesh.getFaceVertices(fc[ref]);
							boolean contains = false;
							for (j = 0; j < vf.length; ++j)
								contains |= selectionRegionContains(screenVert[vf[j]]);
							if (contains)
								selected[i] = newsel;

						}
				}

				else {
					for (i = 0; i < trueFaces.length; i++)
						if (hideFace == null || !hideFace[i]) {
							if (mirror)
								ref = faceTable[i];
							else
								ref = i;
							if (ref < 0)
								continue;
							int[] vf = viewMesh.getFaceVertices(fc[ref]);
							boolean contains = true;
							for (j = 0; j < vf.length; ++j)
								contains &= selectionRegionContains(screenVert[vf[j]]);
							if (contains)
								selected[i] = newsel;

						}
				}

			}
		}
		draggingBox = draggingSelectionBox = false;

		// Send the event to the current tool, if appropriate.
		if (sentClick) {
			if (!dragging) {
				Point p = e.getPoint();
				e.translatePoint(clickPoint.x - p.x, clickPoint.y - p.y);
			}
			if (activeTool instanceof AdvancedEditingTool) {
				Iterator iter = manipulators.iterator();
				while (iter.hasNext()) {
					Manipulator manipulator = ((Manipulator) iter.next());
					if (manipulator.mouseReleased(e))
						break;
				}
			} else
				activeTool.mouseReleased(e, this);
		}

		// If the user shift-clicked a selected point and released the mouse
		// without dragging,
		// then deselect the point.

		if (deselect > -1)
			selected[deselect] = false;
		for (int k = 0; k < selected.length; k++)
			if (selected[k] != oldSelection[k]) {
				currentTool.getWindow().setUndoRecord(
						new UndoRecord(currentTool.getWindow(), false,
								UndoRecord.SET_MESH_SELECTION, new Object[] {
										controller,
										new Integer(controller
												.getSelectionMode()),
										oldSelection }));
				controller.setSelection(selected);
				break;
			}
		currentTool.getWindow().updateMenus();
	}

	/** Set the currently selected tool. */

	public void setTool(EditingTool tool) {
		manipulators.clear();
		if (tool instanceof AdvancedEditingTool)
			((AdvancedEditingTool) tool).activateManipulators(this);
		super.setTool(tool);
	}

	/**
	 * Determine which vertex, edge, or face (depending on the current selection
	 * mode) the mouse was clicked on. If the click was on top of multiple
	 * objects, priority is given to ones which are currently selected, and then
	 * to ones which are in front. If the click is not over any object, -1 is
	 * returned.
	 * 
	 * @param pos
	 *            Description of the Parameter
	 * @param uvw
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */

	public int findClickTarget(Point pos, Vec3 uvw) {
		PolyMesh polymesh = (PolyMesh) getController().getObject().object;
		int loose = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller)
				.getLooseSelectionRange()
				: 0);
		int handleSize = polymesh.getHandleSize();
		Wedge[] sed = null;
		int projectedEdge[] = null;
		PolyMesh viewMesh = polymesh;

		// First, draw any unselected portions of the object.
		int ref = 0;
		boolean mirror = false;
		int[] invEdgeTable = polymesh.invMirroredEdges;
		int[] invVertTable = polymesh.getInvMirroredVerts();
		;
		int[] invFaceTable = polymesh.getInvMirroredFaces();
		Vec3[] normals = null;
		if (polymesh.getMirrorState() != PolyMesh.NO_MIRROR) {
			mirror = true;
			viewMesh = polymesh.getMirroredMesh();
		}
		MeshVertex pv[] = viewMesh.getVertices();
		Wedge[] ed = viewMesh.getEdges();
		Wface[] fc = viewMesh.getFaces();

		if (controller instanceof PolyMeshEditorWindow)
			projectedEdge = ((PolyMeshEditorWindow) controller)
					.findProjectedEdges();
		PolyMesh submesh = null;
		if (projectedEdge != null) {
			submesh = ((PolyMeshEditorWindow) controller)
					.getSubdividedPolyMesh();
			sed = submesh.getEdges();
		} else
			submesh = polymesh;
		Wvertex[] trueVerts = (Wvertex[])polymesh.getVertices();
		Wedge[] trueEdges = polymesh.getEdges();
		Wface[] trueFaces = polymesh.getFaces();
		MeshVertex vt[] = (Wvertex[]) submesh.getVertices();
		double u;
		double v;
		double w;
		double z;
		double closestz = Double.MAX_VALUE;
		boolean sel = false;
		Point v1;
		Point v2;
		int i;
		int which = -1;
		double distance;
		double maxDistance = Double.MAX_VALUE;
		boolean selected[] = controller.getSelection();
		boolean hideFace[] = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller).hideFace
				: new boolean[trueFaces.length]);
		boolean frontSelect = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller)
				.isFrontSelectionOn()
				: false);
		;
		Vec3 viewDir = null;
		if (frontSelect) {
			viewDir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
			normals = polymesh.getFaceNormals();
		}
		if (controller.getSelectionMode() == MeshEditController.POINT_MODE) {
			// System.out.println( screenVert.length + " / " +
			// invVertTable.length);
			int loop = screenVert.length;
			boolean found = false;
			int edge, start, face;
			boolean visibleVert;
			for (i = 0; i < loop; i++) {
				if (mirror)
					ref = invVertTable[i];
				else
					ref = i;
				if (frontSelect) {
					visibleVert =false;
					start = trueVerts[ref].edge;
					edge = start;
					do {
						face = trueEdges[edge].face;
						if (face != -1) {
							if (normals[face].dot(viewDir) < 0.0001) {
								visibleVert = true;
							}
						}
						edge = trueEdges[trueEdges[edge].hedge].next;
					} while (edge != start && !visibleVert);
					if (!visibleVert) {
						continue;
					}
				}
				if (!visible[ref])
					continue;
				if (sel && !selected[ref])
					continue;
				v1 = screenVert[i];
				if (pos.x < v1.x - handleSize / 2 - loose
						|| pos.x > v1.x + handleSize / 2 + loose
						|| pos.y < v1.y - handleSize / 2 - loose
						|| pos.y > v1.y + handleSize / 2 + loose)
					continue;
				distance = (pos.x - v1.x) * (pos.x - v1.x) + (pos.y - v1.y)
						* (pos.y - v1.y);
				z = theCamera.getObjectToView().timesZ(pv[i].r);
				if (distance < maxDistance) {
					maxDistance = distance;
					which = ref;
					closestz = z;
					sel = selected[ref];
					found = true;
				} else if (distance == maxDistance && z < closestz) {
					which = ref;
					closestz = z;
					sel = selected[ref];
					found = true;
				}
			}
			if (!found && screenVertNormals != null) {
				loop = screenVertNormals.length;
				for (i = 0; i < loop; i++) {
					if (mirror)
						ref = invVertTable[i];
					else
						ref = i;
					if (!visible[i])
						continue;
					if (sel && !selected[ref])
						continue;
					v1 = screenVertNormals[i];
					if (v1 == null)
						continue;
					if (pos.x < v1.x - handleSize / 2 - loose
							|| pos.x > v1.x + handleSize / 2 + loose
							|| pos.y < v1.y - handleSize / 2 - loose
							|| pos.y > v1.y + handleSize / 2 + loose)
						continue;
					distance = (pos.x - v1.x) * (pos.x - v1.x) + (pos.y - v1.y)
							* (pos.y - v1.y);
					z = theCamera.getObjectToView().timesZ(pv[i].r);
					if (distance < maxDistance) {
						maxDistance = distance;
						which = ref;
						closestz = z;
						sel = selected[ref];
						found = true;
					} else if (distance == maxDistance && z < closestz) {
						which = ref;
						closestz = z;
						sel = selected[ref];
						found = true;
					}
				}
			}
		} else if (controller.getSelectionMode() == MeshEditController.EDGE_MODE) {
			int loop;
			if (mirror)
				loop = invEdgeTable.length;
			else
				loop = ed.length / 2;
			if (projectedEdge != null)
				loop = submesh.getEdges().length / 2;
			int vv1, vv2;// orv1, orv2;
			boolean visibleEdge;
			for (i = 0; i < loop; i++) {
				int orig;
				vv1 = vv2 = 0;
				if (projectedEdge == null) {
					if (mirror) {
						ref = invEdgeTable[i];
						vv1 = ed[i].vertex;
						vv2 = ed[ed[i].hedge].vertex;
					} else {
						ref = i;
						vv1 = ed[i].vertex;
						vv2 = ed[ed[i].hedge].vertex;
					}
					if (!visible[vv1] || !visible[vv2])
						continue;
					if (frontSelect) {
						visibleEdge =false;
						if (trueEdges[ref].face != -1) {
							if (normals[trueEdges[ref].face].dot(viewDir) < 0.0001) {
								visibleEdge = true;
							}
						}
						if (trueEdges[trueEdges[ref].hedge].face != -1) {
							if (normals[trueEdges[trueEdges[ref].hedge].face].dot(viewDir) < 0.0001) {
								visibleEdge = true;
							}
						}
						if (!visibleEdge) {
							continue;
						}
					}
					if (sel && !selected[ref])
						continue;
					v1 = screenVert[vv1];
					v2 = screenVert[vv2];
					orig = i;
				} else {
					orig = projectedEdge[i];
					if (orig == -1)
						continue;
					if (mirror) {
						if (orig >= invEdgeTable.length)
							continue;
						ref = invEdgeTable[orig];
					} else
						ref = orig;
					if (ref == -1)
						continue;
					if (!visible[vv1] || !visible[vv2])
						continue;
					if (frontSelect && normals[ref].dot(viewDir) > 0.0001) {
						continue;
					}
					if (sel && !selected[ref])
						continue;
					Vec2 screen1 = theCamera.getObjectToScreen().timesXY(
							vt[sed[i].vertex].r);
					Vec2 screen2 = theCamera.getObjectToScreen().timesXY(
							vt[sed[sed[i].hedge].vertex].r);
					v1 = new Point((int) screen1.x, (int) screen1.y);
					v2 = new Point((int) screen2.x, (int) screen2.y);
				}
				if ((pos.x < v1.x - handleSize / 2 - loose && pos.x < v2.x
						- handleSize / 2 - loose)
						|| (pos.x > v1.x + handleSize / 2 + loose && pos.x > v2.x
								+ handleSize / 2 + loose)
						|| (pos.y < v1.y - handleSize / 2 - loose && pos.y < v2.y
								- handleSize / 2 - loose)
						|| (pos.y > v1.y + handleSize / 2 + loose && pos.y > v2.y
								+ handleSize / 2 + loose))
					continue;

				// Determine the distance of the click point from the line.

				if (Math.abs(v1.x - v2.x) > Math.abs(v1.y - v2.y)) {
					if (v2.x > v1.x) {
						v = ((double) pos.x - v1.x) / (v2.x - v1.x);
						u = 1.0 - v;
					} else {
						u = ((double) pos.x - v2.x) / (v1.x - v2.x);
						v = 1.0 - u;
					}
					w = u * v1.y + v * v2.y - pos.y;
				} else {
					if (v2.y > v1.y) {
						v = ((double) pos.y - v1.y) / (v2.y - v1.y);
						u = 1.0 - v;
					} else {
						u = ((double) pos.y - v2.y) / (v1.y - v2.y);
						v = 1.0 - u;
					}
					w = u * v1.x + v * v2.x - pos.x;
				}
				distance = Math.abs(w);
				if (distance > handleSize / 2 + loose)
					continue;
				z = u * theCamera.getObjectToView().timesZ(pv[vv1].r) + v
						* theCamera.getObjectToView().timesZ(pv[vv2].r);
				if (distance < maxDistance) {
					maxDistance = distance;
					closestz = z;
					if (projectedEdge == null && ref >= trueEdges.length / 2)
						ref = trueEdges[ref].hedge;
					which = ref;
					sel = selected[ref];
					if (uvw != null)
						uvw.set(u, v, w);
				} else if (distance < handleSize / 2 && z < closestz) {
					closestz = z;
					if (projectedEdge == null && ref >= trueEdges.length / 2)
						ref = trueEdges[ref].hedge;
					which = ref;
					sel = selected[ref];
					if (uvw != null)
						uvw.set(u, v, w);
				}
			}
		} else {
			int vv;
			Polygon polygon;
			int loop;
			if (mirror)
				loop = invFaceTable.length;
			else
				loop = trueFaces.length;
			for (i = 0; i < loop; i++) {
				if (mirror)
					ref = invFaceTable[i];
				else
					ref = i;
				if (frontSelect && normals[ref].dot(viewDir) > 0.0001) {
					continue;
				}
				if (hideFace != null && hideFace[ref]) {
					continue;
				}
				int[] vf = viewMesh.getFaceVertices(fc[i]);
				boolean whole = true;
				for (int j = 0; j < vf.length; ++j)
					whole |= visible[vf[j]];
				if (!whole) {
					continue;
				}
				if (sel && !selected[ref]) {
					continue;
				}
				polygon = new Polygon();
				Vec3 bary = new Vec3();
				z = 0;
				for (int j = 0; j < vf.length; ++j) {
					vv = vf[j];
					polygon.addPoint(screenVert[vv].x, screenVert[vv].y);
					bary.add(vt[vv].r);
				}
				if (!polygon.contains(pos))
					continue;
				bary.scale(1.0 / (vf.length * 1.0));
				z = theCamera.getObjectToView().timesZ(bary);
				if (z < closestz) {
					which = ref;
					closestz = z;
				}

			}

		}
		return which;
	}

	public void moveToGrid(WidgetMouseEvent e) {
		Point pos = e.getPoint();
		Vec3 v;
		Vec2 v2;

		if (!snapToGrid || perspectiveChoice.getSelectedIndex() == 0)
			return;
		v = theCamera.convertScreenToWorld(pos, theCamera.getDistToScreen());
		v2 = theCamera.getWorldToScreen().timesXY(v);
		e.translatePoint((int) v2.x - pos.x, (int) v2.y - pos.y);
	}

	protected void keyPressed(KeyPressedEvent e) {
		Iterator iter = manipulators.iterator();
		while (iter.hasNext()) {
			if (((Manipulator) iter.next()).keyPressed(e))
				return;
		}
	}

	public void setPerspective(boolean perspective) {
		Iterator iter = manipulators.iterator();
		while (iter.hasNext()) {
			((Manipulator) iter.next()).setPerspective(perspective);
		}
		super.setPerspective(perspective);
	}

	protected void choiceChanged(WidgetEvent ev) {
		super.choiceChanged(ev);
		boolean perspective = isPerspective();
		Iterator iter = manipulators.iterator();
		while (iter.hasNext()) {
			((Manipulator) iter.next()).setPerspective(perspective);
		}
	}

	protected static Color disableColor(Color color) {
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color
				.getBlue(), null);
		hsb[1] *= 0.5;
		return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
	}
}
