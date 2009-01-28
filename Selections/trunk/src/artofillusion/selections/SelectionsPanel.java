/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.selections;

import buoy.widget.*;
import buoy.event.*;
import artofillusion.*;
import artofillusion.object.*;
import artofillusion.ui.*;

import javax.swing.table.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

/**
 * This is the panel which the Selections plugin adds to the main window.
 */

public class SelectionsPanel extends FormContainer
{
  private final LayoutWindow window;
  private final BTable table;
  private final DefaultTableModel model;
  private final BButton newButton, editButton, deleteButton;

  private static final String metadataKey = "selectionsPlugin.selectionSets";
  private static final Icon selectedIcon = new ImageIcon(SelectionsPanel.class.getResource("check.png"));
  private static final Icon partialSelectedIcon = createTransparentIcon(selectedIcon);
  private static final Icon shownIcon = new ImageIcon(SelectionsPanel.class.getResource("eye.png"));
  private static final Icon partialShownIcon = createTransparentIcon(shownIcon);
  private static final Icon lockedIcon = new ImageIcon(SelectionsPanel.class.getResource("lock.png"));
  private static final Icon partialLockedIcon = createTransparentIcon(lockedIcon);

  public SelectionsPanel(LayoutWindow window)
  {
    super(new double[] {1}, new double[] {0, 1});
    this.window = window;
    BToolBar buttons = new BToolBar() {
      public Dimension getMinimumSize()
      {
        return new Dimension(0, 0);
      }
    };
    buttons.add(newButton = Translate.button("new", "...", this, "addSelection"));
    buttons.add(editButton = Translate.button("edit", "...", this, "editSelection"));
    buttons.add(deleteButton = Translate.button("delete", "...", this, "deleteSelection"));
    add(buttons, 0, 0, new LayoutInfo(LayoutInfo.WEST, LayoutInfo.BOTH));
    model = new DefaultTableModel(new Object[] {Translate.text("selectionsPlugin:selectionName"), "", "", ""}, getSelectionSets().size()) {
      @Override
      public Class<?> getColumnClass(int columnIndex)
      {
        return (columnIndex == 0 ? String.class : Integer.class);
      }
      @Override
      public boolean isCellEditable(int row, int column)
      {
        return false;
      }
    };
    table = new BTable(model);
    table.setColumnsResizable(false);
    table.setColumnsReorderable(false);
    table.getComponent().setDefaultRenderer(Integer.class, new TableIconRenderer());
    table.addEventLink(SelectionChangedEvent.class, this, "tableSelectionChanged");
    table.addEventLink(MouseClickedEvent.class, this, "mouseClickedInTable");
    BScrollPane scroll = new BScrollPane(table, BScrollPane.SCROLLBAR_NEVER, BScrollPane.SCROLLBAR_AS_NEEDED);
    add(scroll, 0, 1, new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.BOTH));
    UIUtilities.applyDefaultBackground(this);
    for (int i = 1; i < 4; i++)
    {
      table.getComponent().getColumnModel().getColumn(i).setMaxWidth(selectedIcon.getIconWidth());
      table.setColumnWidth(i, selectedIcon.getIconWidth());
    }
    window.addEventLink(SceneChangedEvent.class, this, "sceneChanged");
    recalculateTableContents();
    tableSelectionChanged();
  }

  /**
   * Get the list of selections for the scene.
   */

  private ArrayList<ObjectSet> getSelectionSets()
  {
    ArrayList<ObjectSet> sets = (ArrayList<ObjectSet>) window.getScene().getMetadata(metadataKey);
    if (sets == null)
      sets = new ArrayList<ObjectSet>();
    return sets;
  }

  /**
   * Set the list of selections for the scene.
   */

  private void setSelectionSets(ArrayList<ObjectSet> sets)
  {
    window.getScene().setMetadata(metadataKey, sets);
  }

  private void sceneChanged()
  {
    newButton.setEnabled(window.getSelectedIndices().length > 0);
    recalculateTableContents();
  }

  private void tableSelectionChanged()
  {
    int selectCount = table.getSelectedRows().length;
    editButton.setEnabled(selectCount == 1);
    deleteButton.setEnabled(selectCount == 1);
  }

  /**
   * Create a new selection from the currently selected objects.
   */

  private void addSelection()
  {
    String name = new BStandardDialog("", Translate.text("selectionsPlugin:enterSelectionName"), BStandardDialog.QUESTION).showInputDialog(window, null, "New Selection");
    if (name == null)
      return;
    ArrayList<ObjectSet> sets = getSelectionSets();
    sets.add(new ObjectSet(name, window.getSelectedObjects().toArray(new ObjectInfo[0])));
    setSelectionSets(sets);
    model.setNumRows(sets.size());
    recalculateTableContents();
  }

  /**
   * Allow the user to edit a selection.
   */

  private void editSelection()
  {
    int[] rows = table.getSelectedRows();
    if (rows.length != 1)
      return;
    ArrayList<ObjectSet> sets = getSelectionSets();
    ObjectSet set = sets.get(rows[0]);
    FormContainer content = new FormContainer(new double[] {0, 1}, new double[] {0, 0, 1});
    LayoutInfo leftLayout = new LayoutInfo(LayoutInfo.WEST, LayoutInfo.NONE, new Insets(2, 2, 2, 2), new Dimension(0, 0));
    content.add(Translate.label("Name"), 0, 0, leftLayout);
    BTextField nameField = new BTextField(set.getName());
    LayoutInfo fillLayout = new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2), new Dimension(0, 0));
    content.add(nameField, 1, 0, fillLayout);
    content.add(Translate.label("selectionsPlugin:objectsInSelection"), 0, 1, 2, 1, leftLayout);
    TreeList tree = new TreeList(window);
    tree.setAllowMultiple(true);
    tree.setUpdateEnabled(false);
    Scene scene = window.getScene();
    for (int i = 0; i < scene.getNumObjects(); i++)
    {
      ObjectInfo info = scene.getObject(i);
      if (info.getParent() == null)
        tree.addElement(new ObjectTreeElement(info, tree));
    }
    for (ObjectInfo info : set.getObjects(scene))
    {
      tree.setSelected(info, true);
      tree.expandToShowObject(info);
    }
    tree.setUpdateEnabled(true);
    BScrollPane scroll = new BScrollPane(tree);
    scroll.setForceWidth(true);
    scroll.setForceHeight(true);
    scroll.setPreferredViewSize(new Dimension(200, 250));
    scroll.getVerticalScrollBar().setUnitIncrement(10);
    content.add(scroll, 0, 2, 2, 1, fillLayout);
    PanelDialog dlg = new PanelDialog(window, "", content);
    if (!dlg.clickedOk())
      return;
    ArrayList<ObjectInfo> objects = new ArrayList<ObjectInfo>();
    for (Object obj : tree.getSelectedObjects())
      objects.add((ObjectInfo) obj);
    sets.set(rows[0], new ObjectSet(nameField.getText(), objects.toArray(new ObjectInfo[objects.size()])));
    setSelectionSets(sets);
    model.setNumRows(sets.size());
    recalculateTableContents();
  }

  /**
   * Delete a selection.
   */

  private void deleteSelection()
  {
    int[] rows = table.getSelectedRows();
    if (rows.length != 1)
      return;
    ArrayList<ObjectSet> sets = getSelectionSets();
    String options[] = new String [] {Translate.text("button.ok"), Translate.text("button.cancel")};
    int choice = new BStandardDialog("", Translate.text("selectionsPlugin:deleteSelectionPrompt",
        sets.get(rows[0]).getName()), BStandardDialog.QUESTION).showOptionDialog(window, options, options[1]);
    if (choice == 0)
    {
      sets.remove(rows[0]);
      setSelectionSets(sets);
      model.setNumRows(sets.size());
      recalculateTableContents();
    }
  }

  private void mouseClickedInTable(MouseClickedEvent ev)
  {
    int row = table.findRow(ev.getPoint());
    int col = table.findColumn(ev.getPoint());
    ArrayList<ObjectSet> sets = getSelectionSets();
    if (col < 0 || col > 3 || row < 0 || row > sets.size())
      return;
    if (col == 0)
    {
      if (ev.getClickCount() == 2)
        editSelection();
      return;
    }
    ObjectSet set = sets.get(row);
    int currentValue = (Integer) table.getCellValue(row, col);
    Scene scene = window.getScene();
    UndoRecord undo = new UndoRecord(window, false);
    if (col == 1)
    {
      // Select or deselect everything in the set.

      undo.addCommand(UndoRecord.SET_SCENE_SELECTION, new Object [] {window.getSelectedIndices()});
      Set<Integer> selectedIndices = new HashSet<Integer>();
      for (int i : window.getSelectedIndices())
        selectedIndices.add(i);
      if (currentValue == 0 || currentValue == 1)
        for (ObjectInfo info : set.getObjects(scene))
          selectedIndices.add(scene.indexOf(info));
      else
        for (ObjectInfo info : set.getObjects(scene))
          selectedIndices.remove(scene.indexOf(info));
      int indexArray[] = new int[selectedIndices.size()];
      int index = 0;
      for (Integer i : selectedIndices)
        indexArray[index++] = i;
      window.setSelection(indexArray);
    }
    if (col == 2)
    {
      // Show or hide everything in the set.

      for (ObjectInfo info : set.getObjects(scene))
      {
        undo.addCommand(UndoRecord.COPY_OBJECT_INFO, new Object [] {info, info.duplicate()});
        info.setVisible(currentValue != 2);
      }
    }
    if (col == 3)
    {
      // Lock or unlock everything in the set.

      for (ObjectInfo info : set.getObjects(scene))
      {
        undo.addCommand(UndoRecord.COPY_OBJECT_INFO, new Object [] {info, info.duplicate()});
        info.setLocked(currentValue != 2);
      }
      window.repaint();
    }
    window.setUndoRecord(undo);
    recalculateTableContents();
    window.updateImage();
  }

  /**
   * Recalculate the contents of the table based on what objects are currently
   * selected, visible, and locked.
   */

  private void recalculateTableContents()
  {
    ArrayList<ObjectSet> sets = getSelectionSets();
    assert sets.size() == table.getRowCount();
    for (int i = 0; i < sets.size(); i++)
    {
      ObjectSet set = sets.get(i);
      model.setValueAt(set.getName(), i, 0);
      boolean allSelected = true, anySelected = false;
      boolean allShown = true, anyShown = false;
      boolean allLocked = true, anyLocked = false;
      for (ObjectInfo info : set.getObjects(window.getScene()))
      {
        if (window.isObjectSelected(info))
          anySelected = true;
        else
          allSelected = false;
        if (info.isVisible())
          anyShown = true;
        else
          allShown = false;
        if (info.isLocked())
          anyLocked = true;
        else
          allLocked = false;
      }
      model.setValueAt(anySelected ? (allSelected ? 2 : 1) : 0, i, 1);
      model.setValueAt(anyShown ? (allShown ? 2 : 1) : 0, i, 2);
      model.setValueAt(anyLocked ? (allLocked ? 2 : 1) : 0, i, 3);
    }
  }

  /**
   * Create an icon by applying 50% transparency to a existing icon.
   */

  private static Icon createTransparentIcon(Icon icon)
  {
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    icon.paintIcon(null, g, 0, 0);
    g.dispose();
    return new ImageIcon(image);
  }

  /**
   * This is the renderer that displays icons in the table.
   */

  private class TableIconRenderer extends DefaultTableCellRenderer
  {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
    {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value == null)
        return component;
      setText(null);
      int mode = (Integer) value;
      setIcon(null);
      if (column == 1)
      {
        if (mode == 1)
          setIcon(partialSelectedIcon);
        else if (mode == 2)
          setIcon(selectedIcon);
      }
      else if (column == 2)
      {
        if (mode == 1)
          setIcon(partialShownIcon);
        else if (mode == 2)
          setIcon(shownIcon);
      }
      else if (column == 3)
      {
        if (mode == 1)
          setIcon(partialLockedIcon);
        else if (mode == 2)
          setIcon(lockedIcon);
      }
      return component;
    }
  }
}
