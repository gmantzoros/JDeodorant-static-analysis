package gr.uom.java.jdeodorant.refactoring.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.context.ObjectContext;
import gr.uom.java.ast.context.ClassContext;
import gr.uom.java.ast.context.ContextBuilder;

/**
 * Action to manually trigger context extraction inside JDeodorant.
 * Appears in the "Bad Smells" menu and builds an ObjectContext for
 * the currently analyzed project.
 */
public class GenerateContextAction implements IWorkbenchWindowActionDelegate {

    private IWorkbenchWindow window;

    @Override
    public void init(IWorkbenchWindow window) {
        this.window = window;
    }

    @Override
    public void run(IAction action) {
        if (window == null) return;

        SystemObject system = ASTReader.getSystemObject();
        if (system == null) {
            MessageDialog.openInformation(window.getShell(), "Generate Context",
                    "No AST found. Please run a JDeodorant analysis first.");
            return;
        }

        MessageDialog.openInformation(window.getShell(), "Generate Context",
                "Building ObjectContext...");

        ObjectContext context = new ObjectContext(system);

        // Attach source code
        ContextBuilder.attachSourceCode(system, context);

        MessageDialog.openInformation(window.getShell(), "Generate Context",
                "Context built!\nClasses: " + context.getTotalClasses() +
                "\nMethods: " + context.getTotalMethods() +
                "\nFields: " + context.getTotalFields());

        // Log to console for debugging
        System.out.println("[GenerateContextAction] " + context);
        for (ClassContext cls : context.getClassContexts()) {
            System.out.println(" → " + cls.getClassName());
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // No action needed when selection changes
    }

    @Override
    public void dispose() {
        // Cleanup resources if necessary
    }
}
