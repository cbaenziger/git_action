package org.apache.oozie.action.hadoop;

import java.io.IOException;
import java.io.StringReader;
import java.io.File;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.oozie.action.hadoop.LauncherMain;
import org.apache.oozie.action.hadoop.LauncherSecurityManager;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;

import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;


import org.apache.oozie.action.ActionExecutor;
import org.apache.oozie.action.ActionExecutorException.ErrorType;
import org.apache.oozie.action.ActionExecutorException;
import org.apache.oozie.client.WorkflowAction;
import org.apache.oozie.service.ConfigurationService;
import org.apache.oozie.service.HadoopAccessorException;
import org.apache.oozie.service.HadoopAccessorService;
import org.apache.oozie.service.Services;
import org.apache.oozie.service.WorkflowAppService;
import org.apache.oozie.util.ELEvaluationException;
import org.apache.oozie.util.LogUtils;
import org.apache.oozie.util.PropertiesUtils;
import org.apache.oozie.util.XConfiguration;
import org.apache.oozie.util.XLog;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.hadoop.conf.Configuration;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.TextProgressMonitor;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;



public class GitMain extends LauncherMain {

    private static final String NODENAME = "git";
    private static final String HDFS_USER = "hdfs";
    private static final String HADOOP_USER = "user.name";
    private static final String HADOOP_JOB_TRACKER = "mapred.job.tracker";
    private static final String HADOOP_JOB_TRACKER_2 = "mapreduce.jobtracker.address";
    private static final String HADOOP_YARN_RM = "yarn.resourcemanager.address";
    private static final String HADOOP_JOB_NAME = "mapred.job.name";

    // Private configuration variables
    private String appName;
    private String workflowId;
    private String callbackUrl;
    private String jobTracker;
    private String nameNode;
    private String keyPath;
    private String destinationUri;
    private String gitUri;
    private String actionType;
    private String actionName;

    private static final Set<String> DISALLOWED_PROPERTIES = new HashSet<String>();

    static {
        DISALLOWED_PROPERTIES.add(HADOOP_USER);
        DISALLOWED_PROPERTIES.add(HADOOP_JOB_TRACKER);
        DISALLOWED_PROPERTIES.add(HADOOP_JOB_TRACKER_2);
        DISALLOWED_PROPERTIES.add(HADOOP_YARN_RM);
    }

    protected XLog LOG = XLog.getLog(getClass());

    public static void main(String[] args) throws Exception {
        run(org.apache.oozie.action.hadoop.GitMain.class, args);
    }

    protected void run(String[] args) throws Exception {
        System.out.println();
        System.out.println("Oozie Git Action Configuration");
        System.out
                .println("=============================================");
        // loading action conf prepared by Oozie
        Configuration actionConf = new Configuration(false);

        String actionXml = System.getProperty("oozie.action.conf.xml");
        if (actionXml == null) {
            throw new RuntimeException(
                    "Missing Java System Property [oozie.action.conf.xml]");
        }
        if (!new File(actionXml).exists()) {
            throw new RuntimeException("Action Configuration XML file ["
                    + actionXml + "] does not exist");
        }

        actionConf.addResource(new Path("file:///", actionXml));

        try {
            parseActionConfiguration(actionConf);
        } catch (RuntimeException e) {
            //convertException(e);
            throw e;
        }

        LOG.debug("Starting " + NODENAME + " for git action");
        final String localKey = getSshKeys(keyPath);

        final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {

            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {

            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch.setConfig("StrictHostKeyChecking", "no");
                JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity(localKey);
                return defaultJSch;
            }
        };
        try {
            cloneRepo(destinationUri, gitUri, sshSessionFactory);
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    private String getSshKeys(String location){
        File key = null;
        Configuration conf = new Configuration();
        FileSystem hfs;
        try {
            hfs = FileSystem.newInstance(new URI(nameNode), conf);
            key = File.createTempFile("keys", Long.toString(System.nanoTime()));
            key.delete();
            key.mkdir();
            hfs.copyToLocalFile(new Path(hfs.getHomeDirectory().toString() + "/" + location + "/privkey.pem"), new Path("file:///" + key.getAbsolutePath() + "/privkey.pem"));
            System.out.println("Copied keys to local container!");
            return(key.getAbsolutePath() + "/privkey.pem");
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private String cloneRepo(String destination, String gitSrc, final SshSessionFactory sshSessionFactory) throws Exception {
        File d = File.createTempFile(destination, Long.toString(System.nanoTime()));
        d.delete();
        System.out.println("Tmp file cycled.");
        d.mkdir();
        System.out.println("Local mkdir called.");

        Configuration conf = new Configuration();
        FileSystem hfs = FileSystem.newInstance(conf);

        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setURI(gitSrc);
        cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport)transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });
        cloneCommand.setDirectory(d);
        cloneCommand.call();

        System.out.println("Finished cloning to local");

        Path src = new Path(d.getAbsolutePath());
        Path dest = new Path(hfs.getHomeDirectory().toString() + "/" + destination);
        System.out.println("Attempting to copy from local now");
        hfs.copyFromLocalFile(false, src, dest);
        System.out.println("Finished the copy (hopefully)!");
        return(dest.toString());
    }



    // Parse action configuration and set configuration variables
    private void parseActionConfiguration(Configuration actionConf) {
        // APP_NAME
        appName = actionConf.get(GitActionExecutor.APP_NAME);
        if (appName == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.APP_NAME + " property");
        }
        //WORKFLOW_ID
        workflowId = actionConf.get(GitActionExecutor.WORKFLOW_ID);
        if (workflowId == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.WORKFLOW_ID + " property");
        }
        // CALLBACK_URL
        callbackUrl = actionConf.get(GitActionExecutor.CALLBACK_URL);
        if (callbackUrl == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.CALLBACK_URL + " property");
        }
        // JOB_TRACKER
        jobTracker = actionConf.get(GitActionExecutor.JOB_TRACKER);
        if (jobTracker == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.JOB_TRACKER + " property");
        }
        //NAME_NODE
        nameNode = actionConf.get(GitActionExecutor.NAME_NODE);
        if (nameNode == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.NAME_NODE + " property");
        }
        // DESTINATION_URI
        destinationUri = actionConf.get(GitActionExecutor.DESTINATION_URI);
        if (destinationUri == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.DESTINATION_URI + " property");
        }
        // GIT_URI
        gitUri = actionConf.get(GitActionExecutor.GIT_URI);
        if (gitUri == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.GIT_URI + " property");
        }
        // KEY_PATH
        keyPath = actionConf.get(GitActionExecutor.KEY_PATH);
        if (keyPath == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.KEY_PATH + " property");
        }
        // ACTION_TYPE
        actionType = actionConf.get(GitActionExecutor.ACTION_TYPE);
        if (actionType == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.ACTION_TYPE + " property");
        }
        // ACTION_NAME
        actionName = actionConf.get(GitActionExecutor.ACTION_NAME);
        if (actionName == null) {
            throw new RuntimeException("Action Configuration does not have "
                    + GitActionExecutor.ACTION_NAME + " property");
        }
    }

}