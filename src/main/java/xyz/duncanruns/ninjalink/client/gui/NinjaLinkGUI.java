package xyz.duncanruns.ninjalink.client.gui;

import org.jetbrains.annotations.NotNull;
import xyz.duncanruns.ninjalink.client.NinjabrainBotConnector;
import xyz.duncanruns.ninjalink.data.Dimension;
import xyz.duncanruns.ninjalink.data.*;
import xyz.duncanruns.ninjalink.util.AngleUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NinjaLinkGUI extends JFrame {
    private final JTable playerTable = new JTable();
    private final JTable strongholdTable = new JTable();
    private final JLabel ninjabrainBotLabel = new JLabel("Not connected to Ninjabrain Bot");
    private boolean discarded = false;

    public NinjaLinkGUI(Runnable onClose, KeyListener keyListener) {
        super();
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        int paddingSize = UIManager.getFont("defaultFont").getSize() / 2;
        constraints.ipady = paddingSize;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(gridBagLayout);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, paddingSize, paddingSize, paddingSize));
        ninjabrainBotLabel.setHorizontalAlignment(SwingConstants.CENTER);
        setContentPane(mainPanel);
        mainPanel.add(ninjabrainBotLabel, constraints);
        JLabel strongholdsLabel = new JLabel("Strongholds");
        strongholdsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(strongholdsLabel, constraints);
        mainPanel.add(strongholdTable, constraints);
        JLabel playersLabel = new JLabel("Players");
        playersLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(playersLabel, constraints);
        mainPanel.add(playerTable, constraints);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (discarded) return;
                onClose.run();
            }
        });
        setTitle("NinjaLink");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addKeyListener(keyListener);
    }

    public void setData(NinjaLinkGroupData groupData, NinjabrainBotEventData myData) {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> setData(groupData, myData));
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        Map<String, PlayerData> playerDataMap = groupData.playerDataMap;
        String[] playerTableHeaders = {"Player", "Position", "Dimension"};
        String[] strongholdTableHeaders = {"Measurer", "%", "Dist.", "Position", "Angle"};
        DefaultTableModel playerTableModel = new DefaultTableModel(0, playerTableHeaders.length) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        DefaultTableModel strongholdTableModel = new DefaultTableModel(0, strongholdTableHeaders.length) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        playerTableModel.addRow(playerTableHeaders);
        strongholdTableModel.addRow(strongholdTableHeaders);
        playerDataMap.forEach((playerName, playerData) -> {
            if (playerData.isEmpty()) return; // this should not be the case but whatever

            playerTableModel.addRow(new String[]{
                    playerName,
                    playerData.position == null ? "" : playerData.position.asBlockPosString(),
                    playerData.dimension == null ? "" : playerData.dimension.toString()
            });
            if (playerData.hasStronghold()) {
                double strongholdDist = 0;
                String strongholdAngleSection = "";
                boolean hasMyPosition = !myData.playerPosition.isEmpty();
                Position strongholdPositionInMyDim = Objects.requireNonNull(playerData.bestStrongholdPrediction).position.translateDimension(Dimension.OVERWORLD, hasMyPosition ? myData.playerPosition.getDimension() : Dimension.NETHER);
                if (hasMyPosition) {
                    Position myPosition = myData.playerPosition.getPosition();
                    strongholdDist = strongholdPositionInMyDim.distanceTo(myPosition);
                    double angleToStronghold = myPosition.angleTo(strongholdPositionInMyDim);
                    double angleDiffToStronghold = AngleUtil.angleDifference(myData.playerPosition.horizontalAngle, angleToStronghold);
                    strongholdAngleSection = String.format(angleDiffToStronghold > 0 ? "%.1f (-> %.1f)" : "%.1f (<- %.1f)", angleToStronghold, Math.abs(angleDiffToStronghold));
                }
                strongholdTableModel.addRow(new String[]{
                        playerName,
                        playerData.hasStronghold() ? String.format("%.1f%%", Objects.requireNonNull(playerData.bestStrongholdPrediction).certainty * 100) : "",
                        hasMyPosition && playerData.hasStronghold() ? String.valueOf((int) Math.floor(strongholdDist)) : "",
                        playerData.hasStronghold() ? (Objects.requireNonNull(strongholdPositionInMyDim).asBlockPosString()) : "",
                        strongholdAngleSection
                });
            }
        });
        playerTable.setModel(playerTableModel);
        strongholdTable.setModel(strongholdTableModel);
        for (JTable table : new JTable[]{playerTable, strongholdTable}) {
            DefaultTableCellRenderer centerRenderer = getNinjaLinkCellRenderer();
            table.setDefaultRenderer(Object.class, centerRenderer);
            table.setFocusable(false);
            table.setRowSelectionAllowed(false);
            table.setCellSelectionEnabled(false);
            resizeColumnWidth(table);
        }
        PercentageCellRenderer percentageCellRenderer = new PercentageCellRenderer();
        percentageCellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        AngleCellRenderer angleCellRenderer = new AngleCellRenderer();
        angleCellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        strongholdTable.getColumnModel().getColumn(1).setCellRenderer(percentageCellRenderer);
        strongholdTable.getColumnModel().getColumn(4).setCellRenderer(angleCellRenderer);

        adjustSize();
    }

    @NotNull
    private static DefaultTableCellRenderer getNinjaLinkCellRenderer() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(Color.getHSBColor(0, 0, row == 0 ? .8f : 1f));
                return c;
            }
        };
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        return centerRenderer;
    }

    public void adjustSize() {
        Container contentPane = getContentPane();
        contentPane.revalidate();

        Insets insets = getInsets();
        java.awt.Dimension contentPref = contentPane.getPreferredSize();
        int requiredHeight = contentPref.height + insets.top + insets.bottom + 50;
        int currentHeight = getHeight();
        int requiredWidth = contentPref.width + insets.right + insets.left + 30;
        int currentWidth = getWidth();

        if (requiredHeight > currentHeight || requiredWidth > currentWidth)
            setSize(Math.max(currentWidth, requiredWidth), Math.max(currentHeight, requiredHeight));
        setMinimumSize(new java.awt.Dimension(requiredWidth, requiredHeight));
    }

    private static void resizeColumnWidth(JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 15;
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 1, width);
            }
            if (width > 300)
                width = 300;
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    public void setNinjabrainBotConnectionState(NinjabrainBotConnector.ConnectionState connectionState) {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> setNinjabrainBotConnectionState(connectionState));
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        switch (connectionState) {
            case CLOSED:
                ninjabrainBotLabel.setText("Not connected to Ninjabrain Bot");
                break;
            case CONNECTED:
                ninjabrainBotLabel.setText("Connected to Ninjabrain Bot");
                break;
        }
    }

    public void setPinned(boolean b) {
        setAlwaysOnTop(b);
        setTitle("NinjaLink" + (b ? " \uD83D\uDCCC" : ""));
    }

    public void discard() {
        discarded = true;
        dispose();
    }

    private static class PercentageCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row == 0) {
                c.setForeground(Color.getHSBColor(0, 0, .8f));
                return c;
            }
            if (value == null) return c;
            try {
                String text = value.toString().replace("%", "");
                float percent = Float.parseFloat(text);
                // Gradient from red (0%) to green (100%)
                float hue = (percent / 100.0f) * 0.333f; // 0.0 (red) to 0.333 (green)
                c.setForeground(Color.getHSBColor(hue, 1.0f, 1f));
            } catch (NumberFormatException e) {
                // Keep default color if parsing fails
            }
            return c;
        }
    }

    private static class AngleCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row == 0) {
                c.setForeground(Color.getHSBColor(0, 0, .8f));
                return c;
            }
            if (value == null) return c;
            String text = value.toString();
            if (text.isEmpty()) return c;

            Matcher matcher = Pattern.compile(".+\\d+\\.\\d+.+?(\\d+\\.\\d+).+").matcher(text);
            if (!matcher.matches()) return c;
            try {
                float angleDiff = Float.parseFloat(matcher.group(1));
                float hue = (1.0f - (angleDiff / 180.0f)) * 0.333f;
                c.setForeground(Color.getHSBColor(hue, 1.0f, 1f));
            } catch (NumberFormatException ignored) {
            }
            return c;
        }
    }
}
