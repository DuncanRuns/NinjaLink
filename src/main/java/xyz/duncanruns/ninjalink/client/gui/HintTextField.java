package xyz.duncanruns.ninjalink.client.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * A text field that shows a hint when it is empty.
 *
 * @author <a href="https://stackoverflow.com/a/1739037">Bart Kiers</a>
 */
public class HintTextField extends JTextField implements FocusListener {

    private final String hint;
    private boolean showingHint;
    private Color hintColor;
    private Color textColor;

    public HintTextField(final String hint) {
        super(hint);
        this.hint = hint;
        this.showingHint = true;
        super.addFocusListener(this);
        
        // Store original text color and set hint color to gray
        this.textColor = getForeground();
        this.hintColor = Color.GRAY;
        setForeground(hintColor);
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (this.getText().isEmpty()) {
            super.setText("");
            showingHint = false;
            setForeground(textColor);
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        if (this.getText().isEmpty()) {
            super.setText(hint);
            showingHint = true;
            setForeground(hintColor);
        }
    }

    @Override
    public String getText() {
        return showingHint ? "" : super.getText();
    }

    @Override
    public void setText(String t) {
        if (showingHint) {
            super.setText(t);
            showingHint = false;
            setForeground(textColor);
        } else {
            super.setText(t);
        }
    }
}