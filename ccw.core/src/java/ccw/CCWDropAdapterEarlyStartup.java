package ccw;

import java.io.File;
import java.io.FileFilter;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

public class CCWDropAdapterEarlyStartup implements IStartup {

	private static final int[] PREFERRED_DROP_OPERATIONS = {
		DND.DROP_LINK, DND.DROP_COPY, DND.DROP_MOVE, DND.DROP_DEFAULT };

	private static final int DROP_OPERATIONS = DND.DROP_MOVE | DND.DROP_COPY
			| DND.DROP_LINK | DND.DROP_DEFAULT;

	private final DropTargetListener dropListener = new CreateProjectDropTargetListener();

	private final FileTransfer transfer = FileTransfer.getInstance();

	private final WorkbenchListener workbenchListener = new WorkbenchListener();

	private Transfer[] transferAgents;

	@Override
	public void earlyStartup() {
		System.out.println("CCW EARLY STARTUP");
		UIJob registerJob = new UIJob(Display.getDefault(),
				"CCWDropAdapterEarlyStartup") {
			{
				setPriority(Job.SHORT);
				setSystem(true);
			}

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				IWorkbench workbench = PlatformUI.getWorkbench();
				workbench.addWindowListener(workbenchListener);
				IWorkbenchWindow[] workbenchWindows = workbench
						.getWorkbenchWindows();
				for (IWorkbenchWindow window : workbenchWindows) {
					workbenchListener.hookWindow(window);
				}
				return Status.OK_STATUS;
			}

		};
		registerJob.schedule();
	}

	public void installDropTarget(final Shell shell) {
		hookUrlTransfer(shell, dropListener);
	}

	private DropTarget hookUrlTransfer(final Shell c,
			DropTargetListener dropTargetListener) {
		DropTarget target = findDropTarget(c);
		if (target != null) {
			// target exists, get it and check proper registration
			registerWithExistingTarget(target, transfer);
		} else {
			target = new DropTarget(c, DROP_OPERATIONS);
			if (transferAgents == null) {
				transferAgents = new Transfer[] { transfer };
			}
			target.setTransfer(transferAgents);
		}
		registerDropListener(target, dropTargetListener);

		hookChildren(c, dropTargetListener);

		return target;
	}

	private void registerDropListener(DropTarget target,
			DropTargetListener dropTargetListener) {
		target.removeDropListener(dropTargetListener);
		target.addDropListener(dropTargetListener);
	}

	private void hookChildren(Control c, DropTargetListener dropTargetListener) {
		if (c instanceof Composite) {
			Control[] children = ((Composite) c).getChildren();
			for (Control child : children) {
				hookRecursive(child, dropTargetListener);
			}
		}
	}

	private void hookRecursive(Control c, DropTargetListener dropTargetListener) {
		DropTarget target = findDropTarget(c);
		if (target != null) {
			// target exists, get it and check proper registration
			registerWithExistingTarget(target, transfer);
			registerDropListener(target, dropTargetListener);
		}
		hookChildren(c, dropTargetListener);
	}

	private static void registerWithExistingTarget(DropTarget target,
			Transfer transfer) {
		Transfer[] transfers = target.getTransfer();
		if (transfers == null)
			return;
		for (Transfer t : transfers) {
			if (transfer.getClass().isInstance(t)) {
				return;
			}
		}
		Transfer[] newTransfers = new Transfer[transfers.length + 1];
		System.arraycopy(transfers, 0, newTransfers, 0, transfers.length);
		newTransfers[transfers.length] = transfer;
		target.setTransfer(newTransfers);
	}

	private DropTarget findDropTarget(Control control) {
		Object object = control.getData(DND.DROP_TARGET_KEY);
		if (object instanceof DropTarget) {
			return (DropTarget) object;
		}
		return null;
	}

	private class CreateProjectDropTargetListener extends DropTargetAdapter {

		@Override
		public void dragEnter(DropTargetEvent e) {
			updateDragDetails(e);
		}

		@Override
		public void dragOver(DropTargetEvent e) {
			updateDragDetails(e);
		}

		@Override
		public void dragLeave(DropTargetEvent e) {
			if (e.detail == DND.DROP_NONE) {
				setDropOperation(e);
			}
		}

		@Override
		public void dropAccept(DropTargetEvent e) {
			updateDragDetails(e);
		}

		@Override
		public void dragOperationChanged(DropTargetEvent e) {
			updateDragDetails(e);
		}

		private void setDropOperation(DropTargetEvent e) {
			int allowedOperations = e.operations;
			for (int op : PREFERRED_DROP_OPERATIONS) {
				if ((allowedOperations & op) != 0) {
					e.detail = op;
					traceDropOperation(e.detail);
					return;
				}
			}
			e.detail = allowedOperations;
			traceDropOperation(e.detail);
		}
		private void traceDropOperation(int op) {
/*
			if ((op & DND.DROP_COPY) != 0)
				System.out.println("DROP_COPY");
			if (op  == DND.DROP_DEFAULT)
				System.out.println("DROP_DEFAULT");
			if ((op & DND.DROP_LINK) != 0)
				System.out.println("DROP_LINK");
			if ((op & DND.DROP_MOVE) != 0)
				System.out.println("DROP_MOVE");
			if (op == DND.DROP_NONE)
				System.out.println("DROP_NONE");
*/
		}

		private void updateDragDetails(DropTargetEvent e) {
			if (dropTargetIsValid(e)) {
				setDropOperation(e);
			}
		}

		private boolean dropTargetIsValid(DropTargetEvent e) {
			return transfer.isSupportedType(e.currentDataType);
		}

		@Override
		public void drop(DropTargetEvent event) {
			if (!transfer.isSupportedType(event.currentDataType)) {
				// ignore
				return;
			}
			if (event.data == null || !dropTargetIsValid(event)) {
				event.detail = DND.DROP_NONE;
				return;
			}
			setDropOperation(event);
			final String[] files = getFilesFromEvent(event);
			if (isPotentialSolution(files)) {
				if (!proceedProjectsCreation(files)) {
					event.detail = DND.DROP_NONE;
				}
			} else {
				event.detail = DND.DROP_NONE;
			}
		}

		private boolean isPotentialSolution(String[] files) {
			return true;
		}

		private String[] getFilesFromEvent(DropTargetEvent event) {
			Object eventData = event.data;
			if (eventData == null)
				return null;
			if (!(eventData instanceof String[]))
				return null;

			String[] files = (String[]) eventData;

			String[] ret = new String[files.length];

			System.arraycopy(files, 0, ret, 0, files.length);

			return ret;
		}

		private boolean proceedProjectsCreation(String[] files) {
			boolean atLeastOneProjectCreated = false;
			for (String f : files) {
				boolean created = proceedProjectCreation(new File(f));
				if (created) {
					atLeastOneProjectCreated = true;
				}
			}
			return atLeastOneProjectCreated;
		}

		private boolean proceedProjectCreation(File file) {

			if (!file.exists() || !file.isDirectory()) {
				CCWPlugin.getTracer().trace(TraceOptions.LOG_INFO,
						"Cannot create project because '" + file.getAbsolutePath() + "' does not exist or is not a directory");
				return false;
			}

			File projectClj = new File(file, "project.clj");

			if (projectClj.exists()) {
				return proceedLeiningenProjectCreation(file);
			}

			// try recursively
			File[] subFolders = file.listFiles(new FileFilter() {
				@Override public boolean accept(File c) {
					return c.isDirectory();
				}
			});
			boolean atLeastOneProjectCreated = false;
			for (File subFolder: subFolders) {
				boolean created = proceedProjectCreation(subFolder);
				if (created) {
					atLeastOneProjectCreated = true;
				}
			}
			return atLeastOneProjectCreated;
	}

	private boolean proceedLeiningenProjectCreation(final File folder) {

		// check no project with file location already
		IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(folder.toURI());
		if (containers.length > 0) {
			CCWPlugin.getTracer().trace(TraceOptions.LOG_INFO, "No project will be created for folder '"
					+ folder.getAbsolutePath() + "': project with same folder exists in workspace");
			return false;
		}

		final String initialProjectName = folder.getName();
		// find a project name matching the folder name
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		Set<String> projectNames = new HashSet<String>(projects.length);
		for (IProject project: projects) {
			projectNames.add(project.getName());
		}
		String maybeProjectName = initialProjectName;
		int i = 1;
		while (projectNames.contains(maybeProjectName)) {
			maybeProjectName = initialProjectName + i;
			i++;
		}

		final String projectName = maybeProjectName;

		// Let's create the eclipse project
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

		WorkspaceJob wj = new WorkspaceJob("Import of project " + projectName + "(path:" + folder.getAbsolutePath() + ")") {

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor)
					throws CoreException {
				IProjectDescription desc = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
				desc.setLocation(new Path(folder.getAbsolutePath()));
				project.create(desc, null);
				project.open(null);

				// Add project to current WorkingSet
				IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
				IWorkingSet[] workingSets = workingSetManager.getWorkingSets();
				if (workingSets != null && workingSets.length > 0) {
					workingSetManager
							.addToWorkingSets(
									project,
							workingSets);
				}
				// TODO nature update should not take place in UI thread ...
				return Status.OK_STATUS;
			}
		};
		wj.setPriority(Job.INTERACTIVE);
		wj.setUser(true);
		wj.schedule();
		return true;
	}
}

	private class WorkbenchListener implements IPartListener2, IPageListener,
			IPerspectiveListener, IWindowListener {

		@Override
		public void perspectiveActivated(IWorkbenchPage page,
				IPerspectiveDescriptor perspective) {
			pageChanged(page);
		}

		@Override
		public void perspectiveChanged(IWorkbenchPage page,
				IPerspectiveDescriptor perspective, String changeId) {
		}

		@Override
		public void pageActivated(IWorkbenchPage page) {
			pageChanged(page);
		}

		@Override
		public void pageClosed(IWorkbenchPage page) {
		}

		@Override
		public void pageOpened(IWorkbenchPage page) {
			pageChanged(page);
		}

		private void pageChanged(IWorkbenchPage page) {
			if (page == null) {
				return;
			}
			IWorkbenchWindow workbenchWindow = page.getWorkbenchWindow();
			windowChanged(workbenchWindow);
		}

		@Override
		public void windowActivated(IWorkbenchWindow window) {
			windowChanged(window);
		}

		private void windowChanged(IWorkbenchWindow window) {
			if (window == null) {
				return;
			}
			Shell shell = window.getShell();
			runUpdate(shell);
		}

		@Override
		public void windowDeactivated(IWorkbenchWindow window) {
		}

		@Override
		public void windowClosed(IWorkbenchWindow window) {
		}

		@Override
		public void windowOpened(IWorkbenchWindow window) {
			hookWindow(window);
		}

		public void hookWindow(IWorkbenchWindow window) {
			window.addPageListener(this);
			window.addPerspectiveListener(this);
			IPartService partService = (IPartService) window
					.getService(IPartService.class);
			partService.addPartListener(this);
			windowChanged(window);
		}

		@Override
		public void partOpened(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partActivated(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partBroughtToTop(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partVisible(IWorkbenchPartReference partRef) {
		}

		@Override
		public void partClosed(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partDeactivated(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partHidden(IWorkbenchPartReference partRef) {
			partUpdate(partRef);
		}

		@Override
		public void partInputChanged(IWorkbenchPartReference partRef) {
		}

		private void partUpdate(IWorkbenchPartReference partRef) {
			IWorkbenchPage page = partRef.getPage();
			pageChanged(page);
		}

		private void runUpdate(final Shell shell) {
			if (shell == null || shell.isDisposed()) {
				return;
			}
			Display display = shell.getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}
			try {
				display.asyncExec(new Runnable() {

					@Override
					public void run() {
						if (!shell.isDisposed()) {
							installDropTarget(shell);
						}
					}
				});
			} catch (SWTException ex) {
				if (ex.code == SWT.ERROR_DEVICE_DISPOSED) {
					// ignore
					return;
				}
				CCWPlugin.getTracer().trace(TraceOptions.LOG_ERROR, ex, "Exception while trying to install/upgrade drop targets");
			} catch (RuntimeException ex) {
				CCWPlugin.getTracer().trace(TraceOptions.LOG_ERROR, ex, "Exception while trying to install/upgrade drop targets");
			}
		}
	}
}
