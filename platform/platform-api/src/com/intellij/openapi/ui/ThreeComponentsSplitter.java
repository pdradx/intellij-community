/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class ThreeComponentsSplitter extends JPanel implements Disposable {
  private int myDividerWidth;
  /**
   *                        /------/
   *                        |  1   |
   * This is vertical split |------|
   *                        |  2   |
   *                        /------/
   *
   *                          /-------/
   *                          |   |   |
   * This is horizontal split | 1 | 2 |
   *                          |   |   |
   *                          /-------/
   */
  private boolean myVerticalSplit;
  private boolean myHonorMinimumSize = false;

  private final Divider myFirstDivider;
  private final Divider myLastDivider;

  private JComponent myFirstComponent;
  private JComponent myInnerComponent;
  private JComponent myLastComponent;

  private int myFirstSize = 10;
  private int myLastSize = 10;

  private boolean myShowDividerControls;
  private int myDividerZone;


  /**
   * Creates horizontal split with proportion equals to .5f
   */
  public ThreeComponentsSplitter() {
    this(false);
  }


  public ThreeComponentsSplitter(boolean vertical) {
    myVerticalSplit = vertical;
    myShowDividerControls = false;
    myFirstDivider = new Divider(true);
    Disposer.register(this, myFirstDivider);
    myLastDivider = new Divider(false);
    Disposer.register(this, myLastDivider);

    myDividerWidth = 7;
    setOpaque(false);
    add(myFirstDivider);
    add(myLastDivider);
  }

  public void setShowDividerControls(boolean showDividerControls) {
    myShowDividerControls = showDividerControls;
    setOrientation(myVerticalSplit);
  }

  public void setDividerMouseZoneSize(int size) {
    myDividerZone = size;
  }

  public boolean isHonorMinimumSize() {
    return myHonorMinimumSize;
  }

  public void setHonorComponentsMinimumSize(boolean honorMinimumSize) {
    myHonorMinimumSize = honorMinimumSize;
  }

  public boolean isVisible() {
    return super.isVisible() && (firstVisible() || innerVisible() || lastVisible());
  }

  private boolean lastVisible() {
    return !Splitter.isNull(myLastComponent) && myLastComponent.isVisible();
  }

  private boolean innerVisible() {
    return !Splitter.isNull(myInnerComponent) && myInnerComponent.isVisible();
  }

  private boolean firstVisible() {
    return !Splitter.isNull(myFirstComponent) && myFirstComponent.isVisible();
  }

  private int visibleDividersCount() {
    int count = 0;
    if (firstDividerVisible()) count++;
    if (lastDividerVisible()) count++;
    return count;
  }

  private boolean firstDividerVisible() {
    return firstVisible() && innerVisible() || firstVisible() && lastVisible() && !innerVisible();
  }

  private boolean lastDividerVisible() {
    return innerVisible() && lastVisible();
  }

  public Dimension getMinimumSize() {
    if (isHonorMinimumSize()) {
      final int dividerWidth = getDividerWidth();
      final Dimension firstSize = myFirstComponent != null ? myFirstComponent.getMinimumSize() : new Dimension(0, 0);
      final Dimension lastSize = myLastComponent != null ? myLastComponent.getMinimumSize() : new Dimension(0, 0);
      final Dimension innerSize = myInnerComponent != null ? myInnerComponent.getMinimumSize() : new Dimension(0, 0);
      if (getOrientation()) {
        int width = Math.max(firstSize.width, Math.max(lastSize.width, innerSize.width));
        int height = visibleDividersCount() * dividerWidth;
        height += firstSize.height;
        height += lastSize.height;
        height += innerSize.height;
        return new Dimension(width, height);
      }
      else {
        int heigth = Math.max(firstSize.height, Math.max(lastSize.height, innerSize.height));
        int width = visibleDividersCount() * dividerWidth;
        width += firstSize.width;
        width += lastSize.width;
        width += innerSize.width;
        return new Dimension(width, heigth);
      }
    }
    return super.getMinimumSize();
  }

  public void doLayout() {
    final int width = getWidth();
    final int height = getHeight();

    Rectangle firstRect = new Rectangle();
    Rectangle firstDividerRect = new Rectangle();
    Rectangle lastDividerRect = new Rectangle();
    Rectangle lastRect = new Rectangle();
    Rectangle innerRect = new Rectangle();
    final int componentSize = getOrientation() ? height : width;
    int dividerWidth = getDividerWidth();
    int dividersCount = visibleDividersCount();

    int firstCompontSize;
    int lastComponentSize;
    int innerComponentSize;
    if(componentSize <= dividersCount * dividerWidth) {
      firstCompontSize = 0;
      lastComponentSize = 0;
      innerComponentSize = 0;
      dividerWidth = componentSize;
    }
    else {
      firstCompontSize = getFirstSize();
      lastComponentSize = getLastSize();
      int sizeLack = firstCompontSize + lastComponentSize - (componentSize - dividersCount * dividerWidth);
      if (sizeLack > 0) {
        // Lacking size. Reduce first component's size, inner -> empty
        firstCompontSize -= sizeLack;
        innerComponentSize = 0;
      }
      else {
        innerComponentSize = componentSize - dividersCount * dividerWidth - getFirstSize() - getLastSize();
      }

      if (!innerVisible()) {
        lastComponentSize += innerComponentSize;
        innerComponentSize = 0;
        if (!lastVisible()) {
          firstCompontSize = componentSize;
        }
      }
    }

    if (getOrientation()) {
      int space = firstCompontSize;
      firstRect.setBounds(0, 0, width, firstCompontSize);
      if (firstDividerVisible()) {
        firstDividerRect.setBounds(0, space, width, dividerWidth);
        space += dividerWidth;
      }

      innerRect.setBounds(0, space, width, innerComponentSize);
      space += innerComponentSize;

      if (lastDividerVisible()) {
        lastDividerRect.setBounds(0, space, width, dividerWidth);
        space += dividerWidth;
      }

      lastRect.setBounds(0, space, width, lastComponentSize);
    }
    else {
      int space = firstCompontSize;
      firstRect.setBounds(0, 0, firstCompontSize, height);

      if (firstDividerVisible()) {
        firstDividerRect.setBounds(space, 0, dividerWidth, height);
        space += dividerWidth;
      }

      innerRect.setBounds(space, 0, innerComponentSize, height);
      space += innerComponentSize;

      if (lastDividerVisible()) {
        lastDividerRect.setBounds(space, 0, dividerWidth, height);
        space += dividerWidth;
      }

      lastRect.setBounds(space, 0, lastComponentSize, height);
    }

    myFirstDivider.setVisible(firstDividerVisible());
    myFirstDivider.setBounds(firstDividerRect);
    myFirstDivider.doLayout();

    myLastDivider.setVisible(lastDividerVisible());
    myLastDivider.setBounds(lastDividerRect);
    myLastDivider.doLayout();

    validateIfNeeded(myFirstComponent, firstRect);
    validateIfNeeded(myInnerComponent, innerRect);
    validateIfNeeded(myLastComponent, lastRect);
  }

  private static void validateIfNeeded(final JComponent c, final Rectangle rect) {
    if (!Splitter.isNull(c)) {
      if (!c.getBounds().equals(rect)) {
        c.setBounds(rect);
        c.revalidate();
      }
    } else {
      Splitter.hideNull(c);
    }
  }


  public int getDividerWidth() {
    return myDividerWidth;
  }

  public void setDividerWidth(int width) {
    if (width < 0) {
      throw new IllegalArgumentException("Wrong divider width: " + width);
    }
    if (myDividerWidth != width) {
      myDividerWidth = width;
      doLayout();
      repaint();
    }
  }

  /**
   * @return <code>true</code> if splitter has vertical orientation, <code>false</code> otherwise
   */
  public boolean getOrientation() {
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit <code>true</code> means that splitter will have vertical split
   */
  public void setOrientation(boolean verticalSplit) {
    myVerticalSplit = verticalSplit;
    myFirstDivider.setOrientation(verticalSplit);
    myLastDivider.setOrientation(verticalSplit);
    doLayout();
    repaint();
  }

  public JComponent getFirstComponent() {
    return myFirstComponent;
  }

  /**
   * Sets component which is located as the "first" splitted area. The method doesn't validate and
   * repaint the splitter. If there is already
   *
   */
  public void setFirstComponent(JComponent component) {
    if (myFirstComponent != component) {
      if (myFirstComponent != null) {
        remove(myFirstComponent);
      }
      myFirstComponent = component;
      if (myFirstComponent != null) {
        add(myFirstComponent);
        myFirstComponent.invalidate();
      }
    }
  }

  public JComponent getLastComponent() {
    return myLastComponent;
  }


  /**
   * Sets component which is located as the "secont" splitted area. The method doesn't validate and
   * repaint the splitter.
   *
   */
  public void setLastComponent(JComponent component) {
    if (myLastComponent != component) {
      if (myLastComponent != null) {
        remove(myLastComponent);
      }
      myLastComponent = component;
      if (myLastComponent != null) {
        add(myLastComponent);
        myLastComponent.invalidate();
      }
    }
  }


  public JComponent getInnerComponent() {
    return myInnerComponent;
  }


  /**
   * Sets component which is located as the "inner" splitted area. The method doesn't validate and
   * repaint the splitter.
   *
   */
  public void setInnerComponent(JComponent component) {
    if (myInnerComponent != component) {
      if (myInnerComponent != null) {
        remove(myInnerComponent);
      }
      myInnerComponent = component;
      if (myInnerComponent != null) {
        add(myInnerComponent);
        myInnerComponent.invalidate();
      }
    }
  }

  public void setFirstSize(final int size) {
    myFirstSize = size;
    doLayout();
    repaint();
  }

  public void setLastSize(final int size) {
    myLastSize = size;
    doLayout();
    repaint();
  }

  public int getFirstSize() {
    return firstVisible() ? myFirstSize : 0;
  }

  public int getLastSize() {
    return lastVisible() ? myLastSize : 0;
  }

  @Override
  public void dispose() {
    myLastComponent = null;
    myFirstComponent = null;
    myInnerComponent = null;
    removeAll();
    Container container = getParent();
    if (container != null) {
      container.remove(this);
    }
  }

  private class Divider extends JPanel implements Disposable {
    protected boolean myDragging;
    protected Point myPoint;
    private final boolean myIsFirst;

    private IdeGlassPane myGlassPane;

    private class MyMouseAdapter extends MouseAdapter implements Weighted {
      @Override
      public void mousePressed(MouseEvent e) {
        _processMouseEvent(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        _processMouseEvent(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        _processMouseMotionEvent(e);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        _processMouseMotionEvent(e);
      }
      @Override
      public double getWeight() {
        return 1;
      }
      private void _processMouseMotionEvent(MouseEvent e) {
        MouseEvent event = getTargetEvent(e);
        if (event == null) {
          myGlassPane.setCursor(null, myListener);
          return;
        }

        processMouseMotionEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }

      private void _processMouseEvent(MouseEvent e) {
        MouseEvent event = getTargetEvent(e);
        if (event == null) {
          myGlassPane.setCursor(null, myListener);
          return;
        }

        processMouseEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }
    }

    private final MouseAdapter myListener = new MyMouseAdapter();


    private MouseEvent getTargetEvent(MouseEvent e) {
      return SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
    }

    private boolean myWasPressedOnMe;

    public Divider(boolean isFirst) {
      super(new GridBagLayout());
      setFocusable(false);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
      myIsFirst = isFirst;
      setOrientation(myVerticalSplit);

      new UiNotifyConnector.Once(this, new Activatable.Adapter() {
        @Override
        public void showNotify() {
          init();
        }
      });
    }

    private boolean isInside(Point p) {
      if (!isVisible()) return false;

      if (myVerticalSplit) {
        if (p.x >= 0 && p.x < getWidth()) {
          if (getHeight() > 0) {
            return p.y >= 0 && p.y < getHeight();
          }
          else {
            return p.y >= -myDividerZone / 2 && p.y <= myDividerZone / 2;
          }
        }
      }
      else {
        if (p.y >= 0 && p.y < getHeight()) {
          if (getWidth() > 0) {
            return p.x >= 0 && p.x < getWidth();
          }
          else {
            return p.x >= -myDividerZone / 2 && p.x <= myDividerZone / 2;
          }
        }
      }

      return false;
    }

    private void init() {
      myGlassPane = IdeGlassPaneUtil.find(this);
      myGlassPane.addMouseMotionPreprocessor(myListener, this);
      myGlassPane.addMousePreprocessor(myListener, this);
    }

    public void dispose() {
    }

    private void setOrientation(boolean isVerticalSplit) {
      removeAll();

      if (!myShowDividerControls) {
        return;
      }

      int xMask = isVerticalSplit ? 1 : 0;
      int yMask = isVerticalSplit ? 0 : 1;

      Icon glueIcon = isVerticalSplit ? AllIcons.General.SplitGlueV : AllIcons.General.SplitCenterH;
      int glueFill = isVerticalSplit ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL;
      add(new JLabel(glueIcon),
          new GridBagConstraints(0, 0, 1, 1, 0, 0, isVerticalSplit ? GridBagConstraints.EAST : GridBagConstraints.NORTH, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      JLabel splitDownlabel = new JLabel(isVerticalSplit ? AllIcons.General.SplitDown : AllIcons.General.SplitRight);
      splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitDownlabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.down.tooltip.text") : UIBundle
        .message("splitter.right.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myInnerComponent != null) {
            final int income = myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth();
            if (myIsFirst) {
              setFirstSize(myFirstSize + income);
            }
            else {
              setLastSize(myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitDownlabel);

      add(splitDownlabel,
          new GridBagConstraints(isVerticalSplit ? 1 : 0,
                                 isVerticalSplit ? 0 : 5,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      //
      add(new JLabel(glueIcon),
          new GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      JLabel splitCenterlabel = new JLabel(isVerticalSplit ? AllIcons.General.SplitCenterV : AllIcons.General.SplitCenterH);
      splitCenterlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitCenterlabel.setToolTipText(UIBundle.message("splitter.center.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          center();
          return true;
        }
      }.installOn(splitCenterlabel);
      add(splitCenterlabel,
          new GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      //
      JLabel splitUpLabel = new JLabel(isVerticalSplit ? AllIcons.General.SplitUp : AllIcons.General.SplitLeft);
      splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitUpLabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.up.tooltip.text") : UIBundle
        .message("splitter.left.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myInnerComponent != null) {
            final int income = myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth();
            if (myIsFirst) {
              setFirstSize(myFirstSize + income);
            }
            else {
              setLastSize(myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitUpLabel);

      add(splitUpLabel,
          new GridBagConstraints(isVerticalSplit ? 5 : 0,
                                 isVerticalSplit ? 0 : 1,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(6 * xMask, 6 * yMask, 1, 1, 0, 0,
                                 isVerticalSplit ? GridBagConstraints.WEST : GridBagConstraints.SOUTH, glueFill, new Insets(0, 0, 0, 0), 0, 0));
    }

    private void center() {
      if (myInnerComponent != null) {
        final int total = myFirstSize + (myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth());
        if (myIsFirst) {
          setFirstSize(total / 2);
        }
        else {
          setLastSize(total / 2);
        }
      }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);

      if (!isShowing()) return;

      if (MouseEvent.MOUSE_DRAGGED == e.getID() && myWasPressedOnMe) {
        myDragging = true;
        setCursor(getResizeCursor());
        myGlassPane.setCursor(getResizeCursor(), myListener);

        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), ThreeComponentsSplitter.this);
        if (getOrientation()) {
          if (getHeight() > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(Math.max(getMinSize(myFirstComponent), myPoint.y));
            }
            else {
              setLastSize(Math.max(getMinSize(myLastComponent), ThreeComponentsSplitter.this.getHeight() - myPoint.y - getDividerWidth()));
            }
          }
        }
        else {
          if (getWidth() > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(Math.max(getMinSize(myFirstComponent), myPoint.x));
            }
            else {
              setLastSize(Math.max(getMinSize(myLastComponent), ThreeComponentsSplitter.this.getWidth() - myPoint.x - getDividerWidth()));
            }
          }
        }
        ThreeComponentsSplitter.this.doLayout();
      } else if (MouseEvent.MOUSE_MOVED == e.getID()) {
        if (myGlassPane != null) {
          if (isInside(e.getPoint())) {
            myGlassPane.setCursor(getResizeCursor(), myListener);
            e.consume();
          } else {
            myGlassPane.setCursor(null, myListener);
          }
        }
      }

      if (myWasPressedOnMe) {
        e.consume();
      }
    }

    private int getMinSize(JComponent component) {
      if (isHonorMinimumSize()) {
        if (component != null && myFirstComponent != null && myFirstComponent.isVisible() && myLastComponent != null && myLastComponent.isVisible()) {
          if (getOrientation()) {
            return component.getMinimumSize().height;
          }
          else {
            return component.getMinimumSize().width;
          }
        }
      }
      return 0;
    }

    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (!isShowing()) {
        return;
      }
      switch (e.getID()) {
        case MouseEvent.MOUSE_ENTERED:
          setCursor(getResizeCursor());
          break;
        case MouseEvent.MOUSE_EXITED:
          if (!myDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
          break;
        case MouseEvent.MOUSE_PRESSED:
          if (isInside(e.getPoint())) {
            myWasPressedOnMe = true;
            myGlassPane.setCursor(getResizeCursor(), myListener);
            e.consume();
          } else {
            myWasPressedOnMe = false;
          }
          break;
        case MouseEvent.MOUSE_RELEASED:
          if (myWasPressedOnMe) {
            e.consume();
          }
          if (isInside(e.getPoint())) {
            myGlassPane.setCursor(getResizeCursor(), myListener);
          }
          myWasPressedOnMe = false;
          myDragging = false;
          myPoint = null;
          break;
        case MouseEvent.MOUSE_CLICKED:
          if (e.getClickCount() == 2) {
            center();
          }
          break;
      }
    }
  }

  private Cursor getResizeCursor() {
    return getOrientation() ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }
}
