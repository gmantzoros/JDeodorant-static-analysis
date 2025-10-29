package gr.uom.java.jdeodorant.refactoring.views;

import gr.uom.java.ast.context.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays class-level context information.
 * Double-click a class row to open a detailed collapsible view (TreeViewer)
 * showing metrics, dependencies, fields, and methods with relationships.
 */
public class ContextView extends ViewPart {

    public static final String ID = "gr.uom.java.jdeodorant.refactoring.views.ContextView";

    private TableViewer viewer;
    private ObjectContext objectContext;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);

        createColumns();
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new ContextLabelProvider());

        // --- Double-click listener ---
        viewer.addDoubleClickListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
            if (selection.getFirstElement() instanceof ClassContext) {
                ClassContext cls = (ClassContext) selection.getFirstElement();
                showClassDetails(cls);
            }
        });
    }

    private void createColumns() {
        String[] titles = { "Class", "Fields", "Methods", "Dependencies", "Dependents" };
        int[] bounds =   { 250, 100, 100, 120, 120 };

        for (int i = 0; i < titles.length; i++) {
            TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
            col.getColumn().setText(titles[i]);
            col.getColumn().setWidth(bounds[i]);
        }
    }

    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /**
     * Populates the table with the new context.
     */
    public void setObjectContext(ObjectContext context) {
        this.objectContext = context;
        List<ClassContext> classes = new ArrayList<>(context.getClassContexts());
        viewer.setInput(classes);
    }

    /**
     * Opens a collapsible detail view for the selected class.
     */
    private void showClassDetails(ClassContext cls) {
        Shell shell = viewer.getControl().getShell();
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
        dialog.setText("Class Details: " + cls.getClassName());
        dialog.setLayout(new FillLayout());
        dialog.setSize(900, 700);

        TreeViewer treeViewer = new TreeViewer(dialog, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        Tree tree = treeViewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        String[] titles = { "Property", "Value" };
        int[] bounds =   { 400, 400 };

        for (int i = 0; i < titles.length; i++) {
            TreeViewerColumn col = new TreeViewerColumn(treeViewer, SWT.NONE);
            col.getColumn().setText(titles[i]);
            col.getColumn().setWidth(bounds[i]);
        }

        // Set content and label providers
        treeViewer.setContentProvider(new TreeContentProvider());
        treeViewer.setLabelProvider(new TreeLabelProvider());

        // Build tree data
        List<TreeNode> rootNodes = buildTreeForClass(cls);
        treeViewer.setInput(rootNodes);

        dialog.open();
    }

    /**
     * Builds the tree structure for a given class context.
     */
    private List<TreeNode> buildTreeForClass(ClassContext cls) {
        List<TreeNode> roots = new ArrayList<>();

        // --- Metrics section ---
        MetricsContext metrics = cls.getMetricsContext();
        if (metrics != null) {
            TreeNode metricsNode = new TreeNode("Metrics", "");
            metricsNode.addChild(new TreeNode("NOM (Number of Methods)", String.valueOf(metrics.getNom())));
            metricsNode.addChild(new TreeNode("NOC (Number of Children)", String.valueOf(metrics.getNoc())));
            metricsNode.addChild(new TreeNode("CBO (Coupling Between Objects)", String.format("%.3f", metrics.getCbo())));
            metricsNode.addChild(new TreeNode("LCOM (Lack of Cohesion)", String.format("%.3f", metrics.getLcom())));
            metricsNode.addChild(new TreeNode("Connectivity", String.format("%.3f", metrics.getConnectivity())));
            metricsNode.addChild(new TreeNode("Fan-In (Dependents)", String.valueOf(metrics.getFanIn())));
            metricsNode.addChild(new TreeNode("Fan-Out (Dependencies)", String.valueOf(metrics.getFanOut())));
            roots.add(metricsNode);
        }

        // --- Dependencies section ---
        TreeNode depsNode = new TreeNode("Dependencies", "");
        TreeNode dependsOnNode = new TreeNode("Depends On (" + cls.getDependencyClasses().size() + ")", "");
        for (ClassContext dep : cls.getDependencyClasses()) {
            dependsOnNode.addChild(new TreeNode("→ " + dep.getClassName(), ""));
        }
        depsNode.addChild(dependsOnNode);

        TreeNode dependedByNode = new TreeNode("Depended By (" + cls.getDependentClasses().size() + ")", "");
        for (ClassContext dep : cls.getDependentClasses()) {
            dependedByNode.addChild(new TreeNode("← " + dep.getClassName(), ""));
        }
        depsNode.addChild(dependedByNode);
        roots.add(depsNode);

        // --- Fields section ---
        if (!cls.getFieldContexts().isEmpty()) {
            TreeNode fieldsNode = new TreeNode("Fields (" + cls.getFieldContexts().size() + ")", "");
            for (FieldContext field : cls.getFieldContexts()) {
                TreeNode fieldNode = new TreeNode("• " + field.getFieldObject().getName(), "");

                if (!field.getReadByMethods().isEmpty()) {
                    TreeNode readNode = new TreeNode("Read by methods", "");
                    for (MethodContext m : field.getReadByMethods()) {
                        readNode.addChild(new TreeNode("← [R] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName(), ""));
                    }
                    fieldNode.addChild(readNode);
                }

                if (!field.getWrittenByMethods().isEmpty()) {
                    TreeNode writeNode = new TreeNode("Written by methods", "");
                    for (MethodContext m : field.getWrittenByMethods()) {
                        writeNode.addChild(new TreeNode("← [W] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName(), ""));
                    }
                    fieldNode.addChild(writeNode);
                }

                fieldsNode.addChild(fieldNode);
            }
            roots.add(fieldsNode);
        }

        // --- Methods section ---
        if (!cls.getMethodContexts().isEmpty()) {
            TreeNode methodsNode = new TreeNode("Methods (" + cls.getMethodContexts().size() + ")", "");
            for (MethodContext m : cls.getMethodContexts()) {
                TreeNode methodNode = new TreeNode("• " + m.getMethodObject().getName(), "");

                if (!m.getCalledMethods().isEmpty()) {
                    TreeNode callsNode = new TreeNode("Calls (" + m.getCalledMethods().size() + ")", "");
                    for (MethodContext called : m.getCalledMethods()) {
                        callsNode.addChild(new TreeNode("→ " + called.getMethodObject().getClassName() + "." + called.getMethodObject().getName(), ""));
                    }
                    methodNode.addChild(callsNode);
                }

                if (!m.getCallerMethods().isEmpty()) {
                    TreeNode calledByNode = new TreeNode("Called By (" + m.getCallerMethods().size() + ")", "");
                    for (MethodContext caller : m.getCallerMethods()) {
                        calledByNode.addChild(new TreeNode("← " + caller.getMethodObject().getClassName() + "." + caller.getMethodObject().getName(), ""));
                    }
                    methodNode.addChild(calledByNode);
                }

                methodsNode.addChild(methodNode);
            }
            roots.add(methodsNode);
        }

        return roots;
    }

    /**
     * Simple data structure for the tree.
     */
    private static class TreeNode {
        String key;
        String value;
        List<TreeNode> children = new ArrayList<>();

        TreeNode(String key, String value) {
            this.key = key;
            this.value = value;
        }

        void addChild(TreeNode node) {
            children.add(node);
        }
    }

    /**
     * Content provider for the tree.
     */
    private static class TreeContentProvider implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof List) {
                return ((List<?>) inputElement).toArray();
            }
            return new Object[0];
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof TreeNode) {
                return ((TreeNode) parentElement).children.toArray();
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof TreeNode && !((TreeNode) element).children.isEmpty();
        }
    }

    /**
     * Label provider for the tree.
     */
    private static class TreeLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int columnIndex) {
            if (element instanceof TreeNode) {
                TreeNode node = (TreeNode) element;
                return columnIndex == 0 ? node.key : node.value;
            }
            return "";
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }

    /**
     * Label provider for the main overview table.
     */
    private static class ContextLabelProvider implements ITableLabelProvider {
        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int columnIndex) { return null; }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            if (element instanceof ClassContext) {
                ClassContext cls = (ClassContext) element;
                switch (columnIndex) {
                    case 0: return cls.getClassName();
                    case 1: return String.valueOf(cls.getFieldContexts().size());
                    case 2: return String.valueOf(cls.getMethodContexts().size());
                    case 3: return String.valueOf(cls.getDependencyClasses().size());
                    case 4: return String.valueOf(cls.getDependentClasses().size());
                }
            }
            return "";
        }

        @Override public void addListener(ILabelProviderListener listener) {}
        @Override public void dispose() {}
        @Override public boolean isLabelProperty(Object element, String property) { return false; }
        @Override public void removeListener(ILabelProviderListener listener) {}
    }
}
