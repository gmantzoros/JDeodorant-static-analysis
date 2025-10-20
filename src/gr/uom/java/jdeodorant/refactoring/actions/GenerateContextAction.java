package gr.uom.java.jdeodorant.refactoring.actions;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.context.ObjectContext;
import gr.uom.java.ast.context.ContextBuilder;
import gr.uom.java.ast.context.ContextDumper;

/**
 * Menu action that rebuilds the AST for the
 * currently selected project, constructs ObjectContext, and dumps it to a file.
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

        try {
            ISelection selection = window.getSelectionService().getSelection();
            IProject project = getSelectedProject(selection);

            if (project == null) {
                MessageDialog.openInformation(window.getShell(), "Generate Context",
                        "Please select a project in the Package Explorer.");
                return;
            }

            // Build IJavaProject and force an AST rebuild
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null || !javaProject.exists()) {
                MessageDialog.openInformation(window.getShell(), "Generate Context",
                        "The selected project is not a Java project.");
                return;
            }

            System.out.println("[GenerateContextAction] Rebuilding AST for: " + project.getName());
            new ASTReader(javaProject, /* IProgressMonitor */ null);
            SystemObject system = ASTReader.getSystemObject();

            if (system == null) {
                MessageDialog.openInformation(window.getShell(), "Generate Context",
                        "Failed to build AST for this project.");
                return;
            }

            MessageDialog.openInformation(window.getShell(), "Generate Context", "Building ObjectContext...");
            ObjectContext context = new ObjectContext(system);

            // Attach source code
            ContextBuilder.attachSourceCode(system, context);

            //Dump info to .txt file
            ContextDumper.dump(context, project.getName());
            MessageDialog.openInformation(window.getShell(), "Generate Context",
                    "Context built successfully!\n\nFile saved.");

        } catch (Exception e) {
            e.printStackTrace();
            MessageDialog.openError(window.getShell(), "Context Generation Error", e.getMessage());
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // No-op
    }

    @Override
    public void dispose() {
        // No-op
    }

    /**
     * Resolve the selected project from various selection types.
     */
    private IProject getSelectedProject(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            Object first = ((IStructuredSelection) selection).getFirstElement();

            if (first instanceof IProject) {
                return (IProject) first;
            }
            if (first instanceof IJavaProject) {
                return ((IJavaProject) first).getProject();
            }
            if (first instanceof IJavaElement) {
                return ((IJavaElement) first).getJavaProject().getProject();
            }
            if (first instanceof IResource) {
                return ((IResource) first).getProject();
            }
            if (first instanceof IAdaptable) {
                IResource r = ((IAdaptable) first).getAdapter(IResource.class);
                if (r != null) return r.getProject();
                IJavaElement je = ((IAdaptable) first).getAdapter(IJavaElement.class);
                if (je != null) return je.getJavaProject().getProject();
            }
        }
        return null;
    }
}
