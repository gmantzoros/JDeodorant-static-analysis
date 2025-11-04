package gr.uom.java.jdeodorant.refactoring.views;

import gr.uom.java.ast.context.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.part.ViewPart;

import freemarker.template.*;
import org.eclipse.jface.dialogs.MessageDialog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
        // 2-column layout: TableViewer (left), Button (right)
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 5;
        layout.marginHeight = 5;
        layout.horizontalSpacing = 8;
        parent.setLayout(layout);

        // --- Table Viewer (fills most space) ---
        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        createColumns();
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new ContextLabelProvider());

        GridData viewerData = new GridData(SWT.FILL, SWT.FILL, true, true);
        viewer.getControl().setLayoutData(viewerData);

        // --- Generate Prompt Button (right side) ---
        Composite buttonContainer = new Composite(parent, SWT.NONE);
        buttonContainer.setLayout(new GridLayout(1, false));
        GridData containerData = new GridData(SWT.RIGHT, SWT.TOP, false, false);
        buttonContainer.setLayoutData(containerData);

        Button generatePromptButton = new Button(buttonContainer, SWT.PUSH);
        generatePromptButton.setText("Generate Prompt");
        GridData buttonData = new GridData(SWT.FILL, SWT.TOP, false, false);
        buttonData.widthHint = 130;
        buttonData.heightHint = 30;
        generatePromptButton.setLayoutData(buttonData);

        generatePromptButton.addListener(SWT.Selection, e -> generatePrompt());

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
        String[] titles = { "Class", "Fields", "Methods"};
        int[] bounds =   { 500, 100, 100};

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

        // Lazily enrich the class before building its details
        if (cls.getMetricsContext() == null || cls.getSourceCode() == null) {
            System.out.println("[ContextView] Enriching class lazily: " + cls.getClassName());
            ContextBuilder.enrichClassContext(objectContext, cls);
        }

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
     * Generates a text prompt using a FreeMarker template.
     */
    private void generatePrompt() {
        IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
        if (selection.isEmpty() || !(selection.getFirstElement() instanceof ClassContext)) {
            MessageDialog.openInformation(viewer.getControl().getShell(),
                    "No Class Selected", "Please select a class first.");
            return;
        }

        ClassContext cls = (ClassContext) selection.getFirstElement();

        // Lazily enrich before prompt generation
        if (cls.getMetricsContext() == null || cls.getSourceCode() == null) {
            System.out.println("[ContextView] Enriching class lazily before prompt: " + cls.getClassName());
            ContextBuilder.enrichClassContext(objectContext, cls);
        }

        try {
            // Setup FreeMarker
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
            cfg.setDefaultEncoding("UTF-8");

            File templateDir;
            try {
                org.osgi.framework.Bundle bundle = org.eclipse.core.runtime.Platform.getBundle("gr.uom.java.jdeodorant");
                java.net.URL entry = bundle.getEntry("templates");
                templateDir = new java.io.File(org.eclipse.core.runtime.FileLocator.toFileURL(entry).getPath());
                cfg.setDirectoryForTemplateLoading(templateDir);
            } catch (Exception ex) {
                MessageDialog.openError(viewer.getControl().getShell(),
                        "Template Error", "Could not locate template directory in plugin bundle.\n" + ex.getMessage());
                return;
            }

            // Let the user select a template
            String[] availableTemplates = templateDir.list((dir, name) -> name.endsWith(".ftl"));
            if (availableTemplates == null || availableTemplates.length == 0) {
                MessageDialog.openInformation(viewer.getControl().getShell(),
                        "No Templates Found", "No .ftl templates found in the 'templates' folder.");
                return;
            }

            ElementListSelectionDialog dialog = new ElementListSelectionDialog(
                    viewer.getControl().getShell(),
                    new LabelProvider()
            );
            dialog.setTitle("Select Template");
            dialog.setMessage("Choose a FreeMarker template to generate the prompt:");
            dialog.setElements(availableTemplates);
            dialog.setMultipleSelection(false);

            if (dialog.open() != Window.OK) {
                return; // user cancelled
            }

            String selectedTemplate = (String) dialog.getFirstResult();
            System.out.println("[ContextView] User selected template: " + selectedTemplate);

            Template template = cfg.getTemplate(selectedTemplate);

            // Build data model
            Map<String, Object> data = buildPromptDataModel(cls);

            // Process template
            StringWriter writer = new StringWriter();
            template.process(data, writer);
            String result = writer.toString();

            showPromptDialog(result);

        } catch (Exception e) {
            e.printStackTrace();
            MessageDialog.openError(viewer.getControl().getShell(), "Template Error", e.getMessage());
        }
    }

    /**
     * Builds data for the FreeMarker template.
     */
    private Map<String, Object> buildPromptDataModel(ClassContext cls) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("className", cls.getClassName());

        MetricsContext metrics = cls.getMetricsContext();
        if (metrics != null) {
            data.put("nom", metrics.getNom());
            data.put("noc", metrics.getNoc());
            data.put("cbo", metrics.getCbo());
            data.put("lcom", metrics.getLcom());
            data.put("connectivity", metrics.getConnectivity());
        }

        List<String> dependsOn = new ArrayList<String>();
        for (ClassContext dep : cls.getDependencyClasses()) {
            dependsOn.add(dep.getClassName());
        }
        data.put("dependsOn", dependsOn);

        List<String> dependedBy = new ArrayList<String>();
        for (ClassContext dep : cls.getDependentClasses()) {
            dependedBy.add(dep.getClassName());
        }
        data.put("dependedBy", dependedBy);

        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        for (FieldContext f : cls.getFieldContexts()) {
            Map<String, Object> fm = new HashMap<String, Object>();
            fm.put("name", f.getFieldObject().getName());

            List<String> readBy = new ArrayList<String>();
            for (MethodContext m : f.getReadByMethods()) {
                readBy.add("← [R] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName());
            }
            fm.put("readBy", readBy);

            List<String> writtenBy = new ArrayList<String>();
            for (MethodContext m : f.getWrittenByMethods()) {
                writtenBy.add("← [W] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName());
            }
            fm.put("writtenBy", writtenBy);

            fields.add(fm);
        }
        data.put("fields", fields);

        List<Map<String, Object>> methods = new ArrayList<Map<String, Object>>();
        for (MethodContext m : cls.getMethodContexts()) {
            Map<String, Object> mm = new HashMap<String, Object>();
            mm.put("name", m.getMethodObject().getName());

            List<String> calls = new ArrayList<String>();
            for (MethodContext called : m.getCalledMethods()) {
                calls.add("→ " + called.getMethodObject().getClassName() + "." + called.getMethodObject().getName());
            }
            mm.put("calls", calls);

            List<String> calledBy = new ArrayList<String>();
            for (MethodContext caller : m.getCallerMethods()) {
                calledBy.add("← " + caller.getMethodObject().getClassName() + "." + caller.getMethodObject().getName());
            }
            mm.put("calledBy", calledBy);

            methods.add(mm);
        }
        data.put("methods", methods);

        String source = cls.getSourceCode();
        data.put("sourceCode", source != null ? source : "[Source unavailable]");
        return data;
    }

    /**
     * Displays the generated prompt in a dialog.
     */
    private void showPromptDialog(String text) {
        Shell shell = viewer.getControl().getShell();
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
        dialog.setText("Generated Prompt");
        dialog.setLayout(new FillLayout());
        dialog.setSize(800, 600);

        Text area = new Text(dialog, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        area.setText(text);
        area.setEditable(false);

        dialog.open();
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
