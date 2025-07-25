package xyz.duncanruns.ninjalink.client.gui;

import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import xyz.duncanruns.ninjalink.client.NinjabrainBotConnector.ConnectionState;
import xyz.duncanruns.ninjalink.data.*;
import xyz.duncanruns.ninjalink.data.Dimension;

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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NinjaLinkGUI extends JFrame {
    private final JTable playerTable = new JTable();
    private final JTable strongholdTable = new JTable();
    private final Spacer strongholdSpacer = new Spacer();
    private boolean discarded = false;
    private final JLabel waitingForDataLabel;
    private final int paddingSize;

    private static ConnectionState nBotState = ConnectionState.CLOSED;

    public NinjaLinkGUI(Runnable onClose, KeyListener keyListener) {
        super();
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        paddingSize = UIManager.getFont("defaultFont").getSize() / 2;
        constraints.ipady = paddingSize;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(gridBagLayout);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, paddingSize, paddingSize, paddingSize));
        setContentPane(mainPanel);
        waitingForDataLabel = new JLabel("Waiting for data...");
        waitingForDataLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(waitingForDataLabel, constraints);
        waitingForDataLabel.setVisible(false);
        mainPanel.add(playerTable, constraints);
        mainPanel.add(strongholdSpacer, constraints);
        mainPanel.add(strongholdTable, constraints);
        constraints.weighty = 1;
        mainPanel.add(new Spacer(), constraints);
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

    public void setData(NinjaLinkGroupData groupData, PlayerData myData) {
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

        AtomicInteger totalStrongholds = new AtomicInteger();
        AtomicInteger totalPlayers = new AtomicInteger();

        playerTableModel.addRow(playerTableHeaders);
        strongholdTableModel.addRow(strongholdTableHeaders);
        playerDataMap.forEach((playerName, playerData) -> {
            if (playerData.isEmpty()) return; // this should not be the case but whatever

            totalPlayers.getAndIncrement();
            playerTableModel.addRow(new String[]{
                    playerName,
                    playerData.position == null ? "" : playerData.position.asBlockPosString(),
                    playerData.dimension == null ? "" : playerData.dimension.toString()
            });
            if (playerData.hasStronghold()) {
                StrongholdPrediction bestStrongholdPrediction = playerData.bestStrongholdPrediction;
                assert bestStrongholdPrediction != null;
                double strongholdDist = bestStrongholdPrediction.distanceFromLastThrow;
                String strongholdAngleSection = String.format("%.1f", bestStrongholdPrediction.angleFromLastThrow);
                Position strongholdPositionInMyDim = Objects.requireNonNull(bestStrongholdPrediction).position.translateDimension(Dimension.OVERWORLD, Optional.ofNullable(myData.dimension).orElse(Dimension.NETHER));
                totalStrongholds.getAndIncrement();
                strongholdTableModel.addRow(new String[]{
                        playerName,
                        String.format("%.1f%%", Objects.requireNonNull(bestStrongholdPrediction).certainty * 100),
                        String.valueOf((int) Math.floor(strongholdDist)),
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

        if (totalPlayers.get() == 0) {
            waitingForDataLabel.setVisible(true);
            strongholdSpacer.setVisible(false);
            strongholdTable.setVisible(false);
            playerTable.setVisible(false);
        } else {
            waitingForDataLabel.setVisible(false);
            playerTable.setVisible(true);
            strongholdSpacer.setVisible(totalStrongholds.get() > 0);
            strongholdTable.setVisible(totalStrongholds.get() > 0);
        }

        adjustSize();
    }

    public void adjustSize() {
        Container contentPane = getContentPane();
        contentPane.revalidate();

        Insets insets = getInsets();
        java.awt.Dimension contentPref = contentPane.getPreferredSize();
        int requiredHeight = contentPref.height + insets.top + insets.bottom + paddingSize + 24;
        int currentHeight = getHeight();
        int requiredWidth = Math.max(38 * paddingSize + 110, contentPref.width + insets.right + insets.left + paddingSize + 24);
        int currentWidth = getWidth();

        setMinimumSize(new java.awt.Dimension(requiredWidth, requiredHeight));

        if (requiredWidth > currentWidth) setSize(requiredWidth, requiredHeight);
        else if (requiredHeight != currentHeight) setSize(getWidth(), requiredHeight);
    }

    public void setNinjabrainBotConnectionState(ConnectionState connectionState) {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> setNinjabrainBotConnectionState(connectionState));
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        nBotState = connectionState;
        updateTitle();
    }

    public void setPinned(boolean b) {
        setAlwaysOnTop(b);
        updateTitle();
    }

    private void updateTitle() {
        setTitle("NinjaLink" + (isAlwaysOnTop() ? " | \uD83D\uDCCC" : "") + " | NbBot: " + (nBotState == ConnectionState.CONNECTED ? "✔" : "❌"));
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
            c.setForeground(Color.getHSBColor(0, 0, 1));
            if (value == null) return c;
            String text = value.toString();
            if (text.isEmpty()) return c;

            Matcher matcher = Pattern.compile(".*\\(.*?(\\d+\\.\\d+).*").matcher(text);
            if (!matcher.matches()) return c;
            float angleDiff = Float.parseFloat(matcher.group(1));
            float hue = (1.0f - (angleDiff / 180.0f)) * 0.333f;
            c.setForeground(Color.getHSBColor(hue, 1.0f, 1f));
            return c;
        }
    }
}
