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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.JColorChooser;



import artofillusion.ModellingApp;
import artofillusion.image.BMPEncoder;
import artofillusion.math.Vec2;
import artofillusion.object.FacetedMesh;
import artofillusion.object.ObjectInfo;
import artofillusion.polymesh.UVMappingData.UVMeshMapping;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedVertex;
import artofillusion.texture.LayeredMapping;
import artofillusion.texture.LayeredTexture;
import artofillusion.texture.Texture;
import artofillusion.texture.TextureMapping;
import artofillusion.texture.UVMapping;
import artofillusion.ui.ActionProcessor;
import artofillusion.ui.Translate;
import buoy.event.CommandEvent;
import buoy.event.MouseDraggedEvent;
import buoy.event.MouseMovedEvent;
import buoy.event.MousePressedEvent;
import buoy.event.MouseReleasedEvent;
import buoy.event.MouseScrolledEvent;
import buoy.event.SelectionChangedEvent;
import buoy.event.ValueChangedEvent;
import buoy.event.WidgetMouseEvent;
import buoy.event.WindowClosingEvent;
import buoy.widget.BButton;
import buoy.widget.BCheckBoxMenuItem;
import buoy.widget.BColorChooser;
import buoy.widget.BComboBox;
import buoy.widget.BDialog;
import buoy.widget.BFileChooser;
import buoy.widget.BFrame;
import buoy.widget.BLabel;
import buoy.widget.BList;
import buoy.widget.BMenu;
import buoy.widget.BMenuBar;
import buoy.widget.BMenuItem;
import buoy.widget.BScrollPane;
import buoy.widget.BSeparator;
import buoy.widget.BSpinner;
import buoy.widget.BSplitPane;
import buoy.widget.BStandardDialog;
import buoy.widget.BTextField;
import buoy.widget.BorderContainer;
import buoy.widget.LayoutInfo;
import buoy.widget.RowContainer;
import buoy.xml.WidgetDecoder;

/**
 * This window allows the user to edit UV mapping using unfolded pieces of mesh
 * displayed over the texture image.
 * 
 * @author Francois Guillet
 * 
 */
public class UVMappingEditorDialog extends BDialog {

    private class ExportImageDialog extends BDialog {

	private BorderContainer borderContainer1;

	private BSpinner widthSpinner;

	private BSpinner heightSpinner;

	private BTextField fileTextField;

	private BButton fileButton;

	private BButton okButton;

	private BButton cancelButton;

	public int width;

	public int height;

	public boolean clickedOk;

	public File file;

	public ExportImageDialog(int width, int height) {
	    super(UVMappingEditorDialog.this, true);
	    this.width = width;
	    this.height = height;
	    setTitle(PMTranslate.text("exportImageFile"));
	    InputStream inputStream = null;
	    try {
		WidgetDecoder decoder = new WidgetDecoder(
			inputStream = getClass().getResource(
				"interfaces/exportImage.xml").openStream(),
			PMTranslate.getResources());
		borderContainer1 = (BorderContainer) decoder.getRootObject();
		widthSpinner = ((BSpinner) decoder.getObject("widthSpinner"));
		widthSpinner.setValue(new Integer(width));
		heightSpinner = ((BSpinner) decoder.getObject("heightSpinner"));
		heightSpinner.setValue(new Integer(height));
		fileTextField = ((BTextField) decoder
			.getObject("fileTextField"));
		fileButton = ((BButton) decoder.getObject("fileButton"));
		okButton = ((BButton) decoder.getObject("okButton"));
		cancelButton = ((BButton) decoder.getObject("cancelButton"));
		okButton.addEventLink(CommandEvent.class, this, "doOK");
		cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
		fileButton.addEventLink(CommandEvent.class, this,
			"doChooseFile");
		fileTextField.addEventLink(ValueChangedEvent.class, this,
			"doFilePathChanged");
		this.addEventLink(WindowClosingEvent.class, this, "doCancel");
		setContent(borderContainer1);
	    } catch (IOException ex) {
		ex.printStackTrace();
	    } finally {
		try {
		    if (inputStream != null)
			inputStream.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	    }
	    file = null;
	    pack();
	    setVisible(true);
	}

	@SuppressWarnings("unused")
	private void doOK() {
	    clickedOk = true;
	    width = ((Integer) widthSpinner.getValue()).intValue();
	    height = ((Integer) heightSpinner.getValue()).intValue();
	    dispose();
	}

	@SuppressWarnings("unused")
	private void doCancel() {
	    clickedOk = false;
	    dispose();
	}

	@SuppressWarnings("unused")
	private void doChooseFile() {
	    BFileChooser chooser = new BFileChooser(BFileChooser.SAVE_FILE,
		    PMTranslate.text("chooseExportImageFile"));
	    if (file != null) {
		chooser.setDirectory(file.getParentFile());
	    }
	    if (chooser.showDialog(UVMappingEditorDialog.this)) {
		file = chooser.getSelectedFile();
		fileTextField.setText(file.getAbsolutePath());
	    }
	}

	@SuppressWarnings("unused")
	private void doFilePathChanged() {
	    file = new File(fileTextField.getText());
	}
    }

    private UVMappingCanvas mappingCanvas;

    private BList pieceList;

    private UVMappingData mappingData;

    private MeshPreviewer preview;

    protected ActionProcessor mouseProcessor;

    protected UVMappingManipulator manipulator;

    private ObjectInfo objInfo;

    private Vec2 scaleOrigin;

    private Vec2[][] originalPositions;

    private UVMeshMapping currentMapping;

    private int currentTexture;

    private ArrayList<Texture> texList;

    private ArrayList<UVMapping> mappingList;
    
    private ArrayList<Vec2[][]> oldCoordList;
    
    private UVMappingData oldMappingData;
    
    private boolean clickedOk;

    /* Interface variables */
    private BorderContainer borderContainer1;

    private BLabel componentLabel;

    private BComboBox componentCB;

    private BLabel uMinValue;

    private BLabel uMaxValue;

    private BLabel vMinValue;

    private BLabel vMaxValue;

    private BLabel resLabel;

    private BSpinner resSpinner;

    private BComboBox mappingCB;
    
    private BLabel textureLabel;

    private BComboBox textureCB;

    private PolymeshValueWidget valueWidget;

    private Runnable validateWidgetValue;

    private Runnable abortWidgetValue;

    private BMenuBar menuBar;
    
    private BMenu sendTexToMappingMenu;
    
    private BMenuItem removeMappingMenuItem;
    
    private BCheckBoxMenuItem[] mappingMenuItems;

    public UVMappingEditorDialog(String title,
	    ObjectInfo objInfo, boolean initialize, BFrame parent) {
	super(parent, title, true);
	this.objInfo = objInfo;
	PolyMesh mesh = (PolyMesh) objInfo.object;
	mappingData = mesh.getMappingData();
	oldMappingData = mappingData.duplicate();
	// find out the UVMapped texture on parFacePerVertex basis
	texList = new ArrayList<Texture>();
	mappingList = new ArrayList<UVMapping>();
	oldCoordList = new ArrayList<Vec2[][]>();
	Texture tex = objInfo.object.getTexture();
	TextureMapping mapping = objInfo.object.getTextureMapping();
	if (tex instanceof LayeredTexture) {
//	    LayeredTexture layeredTex = (LayeredTexture) tex;
	    LayeredMapping layeredMapping = (LayeredMapping) mapping;
	    Texture[] textures = layeredMapping.getLayers();
	    for (int i = 0; i < textures.length; i++) {
		mapping = layeredMapping.getLayerMapping(i);
		if (mapping instanceof UVMapping) {
		    if (((UVMapping) mapping).isPerFaceVertex(mesh)) {
			texList.add(textures[i]);
			mappingList.add((UVMapping) mapping);
			oldCoordList.add(((UVMapping) mapping).findFaceTextureCoordinates((FacetedMesh)objInfo.object));
		    }
		}
	    }
	} else {
	    if (mapping instanceof UVMapping) {
		if (((UVMapping) mapping).isPerFaceVertex(mesh)) {
		    texList.add(tex);
		    mappingList.add((UVMapping) mapping);
		    oldCoordList.add(((UVMapping) mapping).findFaceTextureCoordinates((FacetedMesh)objInfo.object));
		}
	    }
	}
	if (texList.size() == 0) {
	    texList = null;
	    mappingList = null;
	    oldCoordList = null;
	}
	currentTexture = -1;
	initializeMappingsTextures();
	currentMapping = mappingData.getMappings().get(0);
	if (texList != null) {
	    for (int i = 0; i < texList.size(); i++) {
		boolean hasTexture = false;
		for (int j = 0; j < mappingData.mappings.size(); j++) {
		    ArrayList<Integer> textures = mappingData.mappings.get(i).textures;
		    for (int k = 0; k < textures.size(); k++) {
			if (getTextureFromID(textures.get(k)) != -1) {
			    hasTexture = true;
			}
		    }
		}
		if (!hasTexture) {
		    currentMapping.textures.add(texList.get(i).getID());
		}
	    }
	}
	if (currentMapping.textures.size() > 0) {
	    currentTexture = getTextureFromID(currentMapping.textures.get(0));
	}
	BorderContainer content = new BorderContainer();
	setContent(content);
	RowContainer buttons = new RowContainer();
	buttons.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER,
		LayoutInfo.NONE, new Insets(2, 2, 2, 2), new Dimension(0, 0)));
	buttons.add(Translate.button("ok", this, "doOk"));
	buttons.add(Translate.button("cancel", this, "doCancel"));
	content.add(buttons, BorderContainer.SOUTH, new LayoutInfo(
		LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(2, 2, 2, 2),
		new Dimension(0, 0)));
	InputStream inputStream = null;
	try {
	    WidgetDecoder decoder = new WidgetDecoder(inputStream = getClass()
		    .getResource("interfaces/unfoldEditor.xml").openStream(),
		    PMTranslate.getResources());
	    borderContainer1 = (BorderContainer) decoder.getRootObject();
//	    columnContainer1 = ((ColumnContainer) decoder
//		    .getObject("ColumnContainer1"));
	    uMinValue = ((BLabel) decoder.getObject("uMinValue"));
	    uMaxValue = ((BLabel) decoder.getObject("uMaxValue"));
	    vMinValue = ((BLabel) decoder.getObject("vMinValue"));
	    vMaxValue = ((BLabel) decoder.getObject("vMaxValue"));
	    resLabel = ((BLabel)decoder.getObject("resLabel"));
//	    columnContainer3 = ((ColumnContainer) decoder.getObject("ColumnContainer3"));
	    mappingCB = ((BComboBox) decoder.getObject("mappingCB"));
	    mappingCB.addEventLink(ValueChangedEvent.class, this, "doMappingChanged");
	    textureLabel = ((BLabel) decoder.getObject("textureLabel"));
	    textureCB = ((BComboBox) decoder.getObject("textureCB"));
	    textureCB.addEventLink(ValueChangedEvent.class, this, "doTextureChanged");
	    componentLabel = ((BLabel) decoder.getObject("componentLabel"));
	    componentCB = ((BComboBox) decoder.getObject("componentCB"));
	    content.add(borderContainer1, BorderContainer.WEST, new LayoutInfo(
		    LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2),
		    new Dimension(0, 0)));
	    ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
	    for (int i = 0; i < mappings.size(); i++) {
		mappingCB.add(mappings.get(i).name);
	    }
	    ArrayList<Integer> textures = currentMapping.textures;
	    for (int i = 0; i < textures.size(); i++) {
		textureCB.add(texList.get(getTextureFromID(textures.get(i))).getName());
	    }
	    componentCB.setContents(new String[] { Translate.text("Diffuse"),
		    Translate.text("Specular"), Translate.text("Transparent"),
		    Translate.text("Hilight"), Translate.text("Emissive") });
	    componentCB.addEventLink(ValueChangedEvent.class, this,
		    "doChangeComponent");
	    resSpinner = ((BSpinner) decoder.getObject("resSpinner"));
	    resSpinner.setValue(new Integer(mappingData.sampling));
	    resSpinner.addEventLink(ValueChangedEvent.class, this,
		    "doSamplingChanged");
	    content.add(valueWidget = new PolymeshValueWidget(),
		    BorderContainer.EAST);
	} catch (IOException ex) {
	    ex.printStackTrace();
	} finally {
	    try {
		if (inputStream != null)
		    inputStream.close();
	    } catch (IOException ex) {
		ex.printStackTrace();
	    }
	}
	validateWidgetValue = new Runnable() {

	    public void run() {
		doValueWidgetValidate();
	    }
	};
	abortWidgetValue = new Runnable() {

	    public void run() {
		doValueWidgetAbort();
	    }
	};
	BSplitPane meshViewPanel = new BSplitPane(BSplitPane.VERTICAL,
		pieceList = new BList(), preview = new MeshPreviewer(objInfo,
			150, 150));
	tex = null;
	mapping = null;
	if (currentTexture >= 0) {
	    tex = texList.get(currentTexture);
	    mapping = mappingList.get(currentTexture);
	}
	mappingCanvas = new UVMappingCanvas(this, mappingData, preview, tex,
		(UVMapping) mapping);
	BScrollPane sp = new BScrollPane(mappingCanvas);
	meshViewPanel.setResizeWeight(0.7);
	meshViewPanel.setContinuousLayout(true);
	BSplitPane div = new BSplitPane(BSplitPane.HORIZONTAL, sp,
		meshViewPanel);
	div.setResizeWeight(0.5);
	div.setContinuousLayout(true);
	content.add(div, BorderContainer.CENTER, new LayoutInfo(
		LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2),
		new Dimension(0, 0)));
	UnfoldedMesh[] meshes = mappingData.getMeshes();
	for (int i = 0; i < meshes.length; i++)
	    pieceList.add(meshes[i].getName());
	pieceList.addEventLink(SelectionChangedEvent.class, this,
		"doPieceListSelection");
	addEventLink(WindowClosingEvent.class, this, "doCancel");
	ModellingApp.centerWindow((Window) getComponent());
	mappingCanvas.addEventLink(MousePressedEvent.class, this,
		"processMousePressed");
	mappingCanvas.addEventLink(MouseReleasedEvent.class, this,
		"processMouseReleased");
	mappingCanvas.addEventLink(MouseDraggedEvent.class, this,
		"processMouseDragged");
	mappingCanvas.addEventLink(MouseMovedEvent.class, this,
		"processMouseMoved");
	mappingCanvas.addEventLink(MouseScrolledEvent.class, this,
		"processMouseScrolled");
	manipulator = new UVMappingManipulator(mappingCanvas, this);
	menuBar = new BMenuBar();
	BMenu menu = PMTranslate.menu("edit");
	BMenuItem item = PMTranslate.menuItem("selectAll", this, "doSelectAll");
	menu.add(item);
	item = PMTranslate.menuItem("scaleAll", this, "doScaleAll");
	menu.add(item);
	item = PMTranslate.menuItem("renameSelectedPiece", this, "doRenameSelectedPiece");
	menu.add(item);
	menu.add(new BSeparator());
	item = PMTranslate.menuItem("exportImage", this, "doExportImage");
	menu.add(item);
	menuBar.add(menu);
	menu = PMTranslate.menu("mapping");
	item = PMTranslate.menuItem("fitMappingToImage", this, "doFitMappingToImage");
	menu.add(item);
	item = PMTranslate.menuItem("addMapping", this, "doAddMapping");
	menu.add(item);
	item = PMTranslate.menuItem("duplicateMapping", this, "doDuplicateMapping");
	menu.add(item);
	removeMappingMenuItem = PMTranslate.menuItem("removeMapping", this, "doRemoveMapping");
	menu.add(removeMappingMenuItem);
	item = PMTranslate.menuItem("editMappingColor", this, "doEditMappingColor");
	menu.add(item);
	menu.add(new BSeparator());
	sendTexToMappingMenu = PMTranslate.menu("sendTexToMapping");
	menu.add(sendTexToMappingMenu);
	updateMappingMenu();
	menuBar.add(menu);
	menu = PMTranslate.menu("preferences");
	BCheckBoxMenuItem cbitem = PMTranslate.checkboxMenuItem(
		"showSelectionOnPreview", this, "doShowSelection", true);
	menu.add(cbitem);
	cbitem = PMTranslate.checkboxMenuItem(
			"liveUpdate", this, "doLiveUpdate", true);
	menu.add(cbitem);
	cbitem = PMTranslate.checkboxMenuItem(
			"boldEdges", this, "doBoldEdges", true);
	menu.add(cbitem);
	menuBar.add(menu);
	setMenuBar(menuBar);
	updateState();
	pack();
	setVisible(true);
    }
    
    @SuppressWarnings("unused")
	private void doEditMappingColor() {
    	BColorChooser chooser = new BColorChooser(currentMapping.edgeColor,"chooseEdgeColor");
    	if (chooser.showDialog(this)) {
    		currentMapping.edgeColor = chooser.getColor();
    		mappingCanvas.repaint();
    	}
    }
    
    @SuppressWarnings("unused")
	private void doBoldEdges(CommandEvent evt) {
    	BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
    	mappingCanvas.setBoldEdges(item.getState());
    }
    
    @SuppressWarnings("unused")
	private void doLiveUpdate(CommandEvent evt) {
    	BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
    	preview.setShowSelection(item.getState());
    	manipulator.setLiveUpdate(item.getState());
    }
    
    @SuppressWarnings("unused")
    private void doRenameSelectedPiece() {
	int index = pieceList.getSelectedIndex();
	String oldName = mappingData.meshes[index].getName();
	BStandardDialog dlg = new BStandardDialog(PMTranslate.text("pieceName"),
		PMTranslate.text("enterPieceName"),
		BStandardDialog.QUESTION);
	String res = dlg.showInputDialog(this, null, oldName);
	if (res != null) {
	    mappingData.meshes[index].setName(res);
	    pieceList.replace(index, res);
	}
    }
    
    private void updateState() {
	if (currentTexture > -1) {
	    textureLabel.setEnabled(true);
	    textureCB.setEnabled(true);
	    componentLabel.setEnabled(true);
	    componentCB.setEnabled(true);
	    resLabel.setEnabled(true);
	    resSpinner.setEnabled(true);
	    sendTexToMappingMenu.setEnabled(true);
	    textureCB.setSelectedIndex(currentTexture);
	}
	else {
	    textureLabel.setEnabled(false);
	    textureCB.setEnabled(false);
	    componentLabel.setEnabled(false);
	    componentCB.setEnabled(false);
	    resLabel.setEnabled(false);
	    resSpinner.setEnabled(false);
	    sendTexToMappingMenu.setEnabled(false);
	}
	for (int i = 0; i < mappingData.mappings.size(); i++) {
	    if (mappingData.mappings.get(i) == currentMapping) {
		mappingCB.setSelectedIndex(i);
		mappingMenuItems[i].setState(true);
	    } else {
		mappingMenuItems[i].setState(false);
	    }
	}
    }

    private void updateMappingMenu() {
	sendTexToMappingMenu.removeAll();
	mappingMenuItems = new BCheckBoxMenuItem[mappingData.mappings.size()];
	for (int i = 0; i < mappingData.mappings.size(); i++) {
	    sendTexToMappingMenu.add(mappingMenuItems[i] = new BCheckBoxMenuItem(mappingData.mappings.get(i).name, false));
	    mappingMenuItems[i].addEventLink(CommandEvent.class, this, "doSendToMapping");
	    if (mappingData.mappings.get(i) == currentMapping) {
		mappingMenuItems[i].setState(true);
	    }
	}
    }
    
    
    @SuppressWarnings("unused")
    private void doSendToMapping(CommandEvent ev) {
	BCheckBoxMenuItem item = (BCheckBoxMenuItem) ev.getWidget();
	UVMeshMapping newMapping = null;
	for (int i = 0; i < mappingMenuItems.length; i++) {
	    if (mappingData.mappings.get(i) == currentMapping) {
		UVMeshMapping mapping = mappingData.mappings.get(i); 
		for (int j = 0; j < mapping.textures.size(); j++) {
		    if (texList.get(currentTexture).getID() == mapping.textures.get(j)) {
			mapping.textures.remove(j);
			break;
		    }
		}
		mappingMenuItems[i].setState(false);
	    }
	    if (item == mappingMenuItems[i]) {
		newMapping = mappingData.mappings.get(i);
		newMapping.textures.add(new Integer(texList.get(currentTexture).getID()));
		mappingCanvas.setMapping(newMapping);
	    }
	}
	currentMapping = newMapping;
	updateState();
    }

    private void initializeMappingsTextures() {
	ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
	for (int i = 0; i < mappings.size(); i++) {
	    UVMeshMapping mapping = mappings.get(i);
	    int t;
	    if (mapping.textures.size() > 0) {
		for (int j = mapping.textures.size() - 1; j >= 0; j--) {
		    t = getTextureFromID(mapping.textures.get(j));
		    if (t < 0) {
			mapping.textures.remove(j);
		    }
		}
	    }
	}
    }

    private int getTextureFromID(Integer id) {
	if (texList == null || texList.size() == 0) {
	    return -1;
	}
	for (int i = 0; i < texList.size(); i++) {
	    if (texList.get(i).getID() == id) {
		return i;
	    }
	}
	return -1;
    }
    
    @SuppressWarnings("unused")
    private void doMappingChanged() {
	int index = mappingCB.getSelectedIndex();
	currentMapping = mappingData.mappings.get(index);
	if (currentMapping.textures.size() > 0) {
	    currentTexture = getTextureFromID(currentMapping.textures
		    .get(0));
	} else {
	    currentTexture = -1;
	}
	if (currentTexture >= 0) {
	    mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
	} else {
	    mappingCanvas.setTexture(null, null);
	}
	mappingCanvas.setMapping(currentMapping);
	updateState();
    }
    
    @SuppressWarnings("unused")
    private void doTextureChanged() {
	currentTexture = textureCB.getSelectedIndex();
	ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
	int id = texList.get(currentTexture).getID();
	boolean found = false;
	for (int i = 0; i < mappings.size(); i++) {
	    UVMeshMapping mapping = mappings.get(i);
	    int t;
	    if (mapping.textures.size() > 0) {
		for (int j = mapping.textures.size() - 1; j >= 0; j--) {
		    t = getTextureFromID(mapping.textures.get(j));
		    if (t == id) {
			mappingCB.setSelectedIndex(i);
			currentMapping = mapping;
			mappingCanvas.setTexture(texList.get(currentTexture),
				mappingList.get(currentTexture));
			mappingCanvas.setMapping(mapping);
			found = true;
			break;
		    }
		}
	    }
	    if (found) {
		break;
	    }
	}
	updateState();
    }
    
    @SuppressWarnings("unused")
    private void doRemoveMapping() {
	if (mappingData.mappings.size() == 1) {
	    return;
	}
	ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
	for (int i = 0; i < mappings.size(); i++) {
	    if (mappings.get(i) == currentMapping) {
		mappingCB.remove(i);
		mappings.remove(i);
		UVMeshMapping newMapping = mappings.get(0);
		for (int j = 0; j < currentMapping.textures.size(); j++) {
		    newMapping.textures.add(currentMapping.textures.get(j));
		}
		if (currentTexture == -1 && newMapping.textures.size() > 0) {
		    currentTexture = getTextureFromID(newMapping.textures.get(0));
		    mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
		}
		mappingCanvas.setMapping(newMapping);
		currentMapping = newMapping;
		updateState();
		break;
	    }
	}
    }

    @SuppressWarnings("unused")
    private void doFitMappingToImage() {
	double xmin = Double.MAX_VALUE;
	double xmax = -Double.MAX_VALUE;
	double ymin = Double.MAX_VALUE;
	double ymax = -Double.MAX_VALUE;
	for (int i = 0; i < currentMapping.v.length; i++) {	    
	    for (int j = 0; j < currentMapping.v[i].length; j++) {
		if (currentMapping.v[i][j].x < xmin) {
		    xmin = currentMapping.v[i][j].x;
		}
		if (currentMapping.v[i][j].x > xmax) {
		    xmax = currentMapping.v[i][j].x;
		}
		if (currentMapping.v[i][j].y < ymin) {
		    ymin = currentMapping.v[i][j].y;
		}
		if (currentMapping.v[i][j].y > ymax) {
		    ymax = currentMapping.v[i][j].y;
		}
	    }	    
	}
	if (xmin == xmax || ymin == ymax) {
	    return;
	}
	double uscale = (0.9)/(xmax - xmin);
	double vscale = (0.9)/(ymax - ymin);
	if (uscale < vscale) {
	    vscale = uscale;
	} else {
	    uscale = vscale;
	}
	for (int i = 0; i < currentMapping.v.length; i++) {	    
	    for (int j = 0; j < currentMapping.v[i].length; j++) {
		currentMapping.v[i][j].x = (currentMapping.v[i][j].x - xmin)*uscale + 0.05;
		currentMapping.v[i][j].y = (currentMapping.v[i][j].y - ymin)*vscale + 0.05;
	    }
	}
	mappingCanvas.setRange(0, 1, 0, 1);
	mappingCanvas.repaint();
    }
    
    @SuppressWarnings("unused")
    private void doAddMapping() {
	addMapping(false);
    }
    
    @SuppressWarnings("unused")
    private void doDuplicateMapping() {
	addMapping(true);
    }

    /**
     * Adds another mapping to the available mappings.
     * 
     * @param duplicate Use default vertices positions or duplicate current mapping
     */
    private void addMapping(boolean duplicate) {
	BStandardDialog dlg = new BStandardDialog(PMTranslate.text("addMapping"),
		PMTranslate.text("enterMappingName"),
		BStandardDialog.QUESTION);
	String res = dlg.showInputDialog(this, null, PMTranslate.text("mapping") + " #" + (mappingData.mappings.size() + 1));
	if (res != null) {
	    UVMeshMapping mapping = null;
	    if (duplicate) {
		mapping = mappingData.addNewMapping(res, currentMapping);
	    } else {
		mapping = mappingData.addNewMapping(res, null);
	    }
	    mappingCB.add(mapping.name);
	    mappingCB.setSelectedValue(mapping.name);
	    currentTexture = -1;
	    currentMapping = mapping;
	    mappingCanvas.setTexture(null, null);
	    mappingCanvas.setMapping(mapping);
	    updateMappingMenu();
	    updateState();
	}
    }

    @SuppressWarnings("unused")
    private void doShowSelection(CommandEvent evt) {
	BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
	preview.setShowSelection(item.getState());
	mappingCanvas.setSelection(mappingCanvas.getSelection());
    }

    @SuppressWarnings("unused")
    private void doSelectAll() {
	mappingCanvas.selectAll();
    }

    @SuppressWarnings("unused")
    private void doScaleAll() {
	UVMeshMapping mapping = mappingCanvas.getMapping();
	Vec2[] v;
	originalPositions = new Vec2[mapping.v.length][];
	scaleOrigin = new Vec2();
	int count = 0;
	for (int i = 0; i < mapping.v.length; i++) {
	    v = mapping.v[i];
	    originalPositions[i] = new Vec2[v.length];
	    for (int j = 0; j < v.length; j++) {
		originalPositions[i][j] = new Vec2(v[j]);
		scaleOrigin.add(originalPositions[i][j]);
		count++;
	    }
	}
	scaleOrigin.scale(1.0 / ((double) count));
	valueWidget.setValueMax(5);
	valueWidget.setValueMin(0);
	Runnable callback = new Runnable() {

	    public void run() {
		doScaleCallback();
	    }
	};
	valueWidget.activate(1.0, callback, validateWidgetValue,
		abortWidgetValue);
    }

    private void doScaleCallback() {
	double scale = valueWidget.getValue();
	UVMeshMapping mapping = mappingCanvas.getMapping();
	Vec2[] v;
	for (int i = 0; i < mapping.v.length; i++) {
	    v = mapping.v[i];
	    for (int j = 0; j < v.length; j++) {
		v[j] = (originalPositions[i][j].minus(scaleOrigin)
			.times(scale)).plus(scaleOrigin);
	    }
	}
	mappingCanvas.refreshVerticesPoints();
	mappingCanvas.repaint();
    }

    @SuppressWarnings("unused")
    private void doSamplingChanged() {
	mappingCanvas.setSampling(((Integer) resSpinner.getValue()).intValue());
    }

    @SuppressWarnings("unused")
    private void doExportImage() {
	ExportImageDialog dlg = new ExportImageDialog(500, 500);
	if (!dlg.clickedOk || dlg.file == null) {
	    return;
	}
	File f = dlg.file;
	BufferedImage offscreen = new BufferedImage(dlg.width, dlg.height,
		BufferedImage.TYPE_INT_RGB);
	Graphics2D offscreenGraphics = (Graphics2D) offscreen.getGraphics();
	mappingCanvas.drawOnto(offscreenGraphics, dlg.width, dlg.height);
	try {
	    BufferedOutputStream bos = new BufferedOutputStream(
		    new FileOutputStream(f));
	    DataOutputStream dos = new DataOutputStream(bos);
	    BMPEncoder bmp = new BMPEncoder(offscreen);
	    bmp.writeImage(dos);
	    dos.close();
	} catch (FileNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (InterruptedException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    @SuppressWarnings("unused")
    private void processMousePressed(WidgetMouseEvent ev) {
	if (mouseProcessor != null)
	    mouseProcessor.stopProcessing();
	doMousePressed(ev);
	mouseProcessor = new ActionProcessor();
    }

    @SuppressWarnings("unused")
    private void processMouseDragged(final WidgetMouseEvent ev) {
	if (mouseProcessor != null)
	    mouseProcessor.addEvent(new Runnable() {
		public void run() {
		    doMouseDragged(ev);
		}
	    });
    }
    
    @SuppressWarnings("unused")
    private void processMouseMoved(final WidgetMouseEvent ev) {
	if (mouseProcessor != null)
	    mouseProcessor.addEvent(new Runnable() {
		public void run() {
		    doMouseMoved(ev);
		}
	    });
    }

    @SuppressWarnings("unused")
    private void processMouseReleased(WidgetMouseEvent ev) {
	if (mouseProcessor != null) {
	    mouseProcessor.stopProcessing();
	    mouseProcessor = null;
	    doMouseReleased(ev);
	}
    }

    @SuppressWarnings("unused")
    private void processMouseScrolled(MouseScrolledEvent ev) {
	doMouseScrolled(ev);
    }

    protected void doMousePressed(WidgetMouseEvent ev) {
	manipulator.mousePressed(ev);
    }

    protected void doMouseDragged(WidgetMouseEvent ev) {
	manipulator.mouseDragged(ev);
    }
    
    protected void doMouseMoved(WidgetMouseEvent ev) {
    	manipulator.mouseMoved(ev);
    }

    protected void doMouseReleased(WidgetMouseEvent ev) {
	manipulator.mouseReleased(ev);
    }

    protected void doMouseScrolled(MouseScrolledEvent ev) {
	manipulator.mouseScrolled(ev);
    }

    protected void doChangeComponent() {
	mappingCanvas.setComponent(componentCB.getSelectedIndex());
    }

    @SuppressWarnings("unused")
    private void doOk() {
	clickedOk = true;
	dispose();
    }

    @SuppressWarnings("unused")
    private void doCancel() {
	PolyMesh mesh = (PolyMesh)objInfo.object;
	if (texList != null) {
	    for (int i = 0; i < texList.size(); i++) {
		mappingList.get(i).setFaceTextureCoordinates(mesh, oldCoordList.get(i));
	    }
	}
	mesh.setMappingData(oldMappingData);
	dispose();
    }

    @SuppressWarnings("unused")
    private void doPieceListSelection() {
	mappingCanvas.setSelectedPiece(pieceList.getSelectedIndex());
    }

    /**
         * Works out a default layout when the unfolded meshes are displayed for
         * the first time. Call is forwarded to
         * UVMappingCanvas.initializeMeshLayout().
         * 
         */
    public void initializeMeshLayout() {
	mappingCanvas.resetMeshLayout();
    }

    public void displayUVMinMax(double umin, double umax, double vmin,
	    double vmax) {
	DecimalFormat format = new DecimalFormat();
	format.setMaximumFractionDigits(2);
	uMinValue.setText(format.format(umin));
	vMinValue.setText(format.format(vmin));
	uMaxValue.setText(format.format(umax));
	vMaxValue.setText(format.format(vmax));
    }

    /**
         * Validate button selected
         */
    private void doValueWidgetValidate() {
	// PolyMesh mesh = (PolyMesh) objInfo.object;
	// enableNormalFunction();
	// setUndoRecord( new UndoRecord( this, false, UndoRecord.COPY_OBJECT,
	// new Object[]{mesh, priorValueMesh} ) );
    }

    /**
         * Cancel button selected
         */
    private void doValueWidgetAbort() {
	UVMeshMapping mapping = mappingCanvas.getMapping();
	Vec2[] v;
	for (int i = 0; i < mapping.v.length; i++) {
	    v = mapping.v[i];
	    for (int j = 0; j < v.length; j++) {
		v[j] = originalPositions[i][j];
	    }
	}
	mappingCanvas.refreshVerticesPoints();
	mappingCanvas.repaint();
    }

    /**
         * @return True if the user has clicked on the Ok Button
         */
    public boolean isClickedOk() {
	return clickedOk;
    }

    /**
         * @return the mappingData
         */
    public UVMappingData getMappingData() {
	return mappingData;
    }
}
