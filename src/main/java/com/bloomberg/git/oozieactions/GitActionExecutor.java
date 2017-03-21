package org.apache.oozie.action.hadoop;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.hadoop.util.DiskChecker;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.AccessControlException;
import org.apache.oozie.action.ActionExecutorException;
import org.apache.oozie.action.hadoop.JavaActionExecutor;
import org.apache.oozie.action.hadoop.LauncherMain;
import org.apache.oozie.action.hadoop.LauncherMapper;
import org.apache.oozie.action.hadoop.MapReduceMain;
import org.apache.oozie.action.ActionExecutor;
import org.apache.oozie.action.ActionExecutorException.ErrorType;
import org.apache.oozie.action.ActionExecutorException;
import org.apache.oozie.util.XmlUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;

public class GitActionExecutor extends JavaActionExecutor {

    private static final String GIT_MAIN_CLASS_NAME =
            "org.apache.oozie.action.hadoop.GitMain";
    public static final String APP_NAME = "oozie.oozie.app.name";
    public static final String WORKFLOW_ID = "oozie.oozie.workflow.id";
    public static final String CALLBACK_URL = "oozie.oozie.callback.url";
    public static final String JOB_TRACKER = "oozie.oozie.job.tracker";
    public static final String NAME_NODE = "oozie.oozie.name.node";
    public static final String GIT_URI = "oozie.git.git.uri";
    public static final String DESTINATION_URI = "oozie.git.destination.uri";
    public static final String KEY_PATH = "oozie.oozie.key.path";
    public static final String ACTION_TYPE = "oozie.oozie.action.type";
    public static final String ACTION_NAME = "oozie.oozie.action.name";

    public GitActionExecutor() {
        super("git");
    }

    @Override
    public List<Class> getLauncherClasses() {
        List<Class> classes = super.getLauncherClasses();
        classes.add(LauncherMain.class);
        classes.add(MapReduceMain.class);
        try {
            classes.add(Class.forName(GIT_MAIN_CLASS_NAME));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found", e);
        }
        return classes;
    }

    @Override
    protected String getLauncherMain(Configuration launcherConf, Element actionXml) {
        return launcherConf.get(LauncherMapper.CONF_OOZIE_ACTION_MAIN_CLASS,
                GIT_MAIN_CLASS_NAME);
    }

    @Override
    public void initActionType() {
        super.initActionType();
        registerError(UnknownHostException.class.getName(), ActionExecutorException.ErrorType.TRANSIENT, "HES001");
        registerError(AccessControlException.class.getName(), ActionExecutorException.ErrorType.NON_TRANSIENT,
                "JA002");
        registerError(DiskChecker.DiskOutOfSpaceException.class.getName(),
                ActionExecutorException.ErrorType.NON_TRANSIENT, "HES003");
        registerError(org.apache.hadoop.hdfs.protocol.QuotaExceededException.class.getName(),
                ActionExecutorException.ErrorType.NON_TRANSIENT, "HES004");
        registerError(org.apache.hadoop.hdfs.server.namenode.SafeModeException.class.getName(),
                ActionExecutorException.ErrorType.NON_TRANSIENT, "HES005");
        registerError(ConnectException.class.getName(), ActionExecutorException.ErrorType.TRANSIENT, "  HES006");
        registerError(JDOMException.class.getName(), ActionExecutorException.ErrorType.ERROR, "HES007");
        registerError(FileNotFoundException.class.getName(), ActionExecutorException.ErrorType.ERROR, "HES008");
        registerError(IOException.class.getName(), ActionExecutorException.ErrorType.TRANSIENT, "HES009");
    }

    @Override
    @SuppressWarnings("unchecked")
    Configuration setupActionConf(Configuration actionConf, Context context,
                                  Element actionXml, Path appPath) throws ActionExecutorException {
        super.setupActionConf(actionConf, context, actionXml, appPath);
        Namespace ns = actionXml.getNamespace();

        String appName = context.getWorkflow().getAppName();
        String workflowId = context.getWorkflow().getId();
        String callbackUrl = context.getCallbackUrl("$jobStatus");

        String jobTracker = actionXml.getChild("job-tracker", ns).getTextTrim();
        String nameNode = actionXml.getChild("name-node", ns).getTextTrim();
        String gitUri = actionXml.getChild("git-uri", ns).getTextTrim();
        String destinationUri = actionXml.getChild("destination-uri", ns).getTextTrim();
        String keyPath = actionXml.getChild("key-path", ns).getTextTrim();

        String actionType = getType();
        String actionName = "git";

        actionConf.set(APP_NAME, appName);
        actionConf.set(WORKFLOW_ID, workflowId);
        actionConf.set(CALLBACK_URL, callbackUrl);
        actionConf.set(JOB_TRACKER, jobTracker);
        actionConf.set(NAME_NODE, nameNode);
        actionConf.set(GIT_URI, gitUri);
        actionConf.set(DESTINATION_URI, destinationUri);
        actionConf.set(KEY_PATH, keyPath);
        actionConf.set(ACTION_TYPE, actionType);
        actionConf.set(ACTION_NAME, actionName);
        return actionConf;
    }

    @Override
    protected String getDefaultShareLibName(Element actionXml) {
        return "git";
    }
}