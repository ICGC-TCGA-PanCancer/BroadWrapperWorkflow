package io.seqware.pancancer;

import java.util.Arrays;
import java.util.List;

import net.sourceforge.seqware.pipeline.workflowV2.AbstractWorkflowDataModel;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;

/**
 * <p>
 * For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare Java Workflows</a>.
 * </p>
 * 
 * Quick reference for the order of methods called: 1. setupDirectory 2. setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 * 
 * See the SeqWare API for <a href=
 * "http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">
 * AbstractWorkflowDataModel</a> for more information.
 */
public class BroadWrapperWorkflow extends AbstractWorkflowDataModel {

    //private String jobsDir;
    private String workflowID;
    private String workflowDir;
    private boolean cleanup;
    private boolean checkWorkflowFileExists = false;
    private List<String> uploadSourceDirs = Arrays.asList("muse","broad","broad_tar");

    private String pcawgContainerName ;

    private void init() {
        try {
            
//            if (hasPropertyAndNotNull("jobs_dir")){
//                this.jobsDir = getProperty("jobs_dir");
//            }
//            else
//            {
//                throw new RuntimeException("\"jobs_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
//            }

            if (hasPropertyAndNotNull("workflow_dir")){
                this.workflowDir = getProperty("workflow_dir");
            }
            else
            {
                throw new RuntimeException("\"workflow_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }
            
            if (hasPropertyAndNotNull("workflow_id")){
                this.workflowID = getProperty("workflow_id");
            }
            else
            {
                throw new RuntimeException("\"workflow_id\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }

            if (hasPropertyAndNotNull("container_name")){
                this.pcawgContainerName = getProperty("container_name");
            }
            else
            {
                throw new RuntimeException("\"container_name\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }            
            
            //This is optional - if the user doesn't specify, we'll skip the check.
            if (hasPropertyAndNotNull("check_workflowfile_exists")) {
                this.checkWorkflowFileExists = Boolean.valueOf( getProperty("check_workflowfile_exists") );
            }
//            if (hasPropertyAndNotNull("pcawg_dir")){
//                this.pcawgDir = getProperty("pcawg_dir");
//            }
//            else
//            {
//                throw new RuntimeException("\"pcawg_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
//            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    @Override
//    public void setupDirectory() {
//        // since setupDirectory is the first method run, we use it to initialize variables too.
//        init();
//        // creates a dir1 directory in the current working directory where the workflow runs
//        this.addDirectory(jobsDir);
//    }

    @Override
    public void buildWorkflow() {
        this.init();
       
        // Generate workflow files. Yes, we are generating for ALL that are registered for the user with synapse credentials in this machine, but this
        // is not a very expensive step with no side-effects and the INI will specify exactly which INI to use.
        Job generateJobs = this.generateWorkflowFilesJob();

        // TODO: Add a bash job to check that the workflow file actually exists where it is supposed to and returns an error code if it does not.
        // The Broad scripts may fail if the file does not exist, but they may
        // not return a non-zero error code, so this workflow will finish very quickly and *appear* successful when it is not.
        Job checkforWFFile = this.checkForWorkflowFile(this.workflowID, generateJobs);
        
        // Run the workflow.
        Job runBroad = this.runBroadWorkflow(this.workflowID, checkforWFFile);
        //cleanupWorkflow(runBroad);
        
        // TODO: Maybe  have a post-Broad step that outputs the <WORKFLOWID>.err and <WORKFLOWID>.out files so that they are a part of the seqware output?
        // It seems like a lot of Broad scripts will exit with code 0 even if they encountered an error (missing input file, HTTP timeout, etc...) and ended
        // so SeqWare might "succeed" even when Broad fails.
        
        // TODO: Upload steps! Not yet certain if there will be one upload script wrapping the other steps, or if this workflow will call them all separately.
        
    }
    
//    private void cleanupWorkflow(Job... lastJobs) {
//        Job cleanupJob = null;
//        if (cleanup) {
//            cleanupJob = this.getWorkflow().createBashJob("cleanup");
//            // Clean up the jobsDir 
//            cleanupJob.getCommand().addArgument("rm -Rf "+this.jobsDir+" \n");
//        }
//        for (Job lastJob : lastJobs) {
//            if (lastJob != null && cleanupJob != null) {
//                cleanupJob.addParent(lastJob);
//            }
//        }
//    }
    
 
    private Job generateWorkflowFilesJob()
    {
        Job generateWFFilesJob = this.getWorkflow().createBashJob("generate_broad_workflow_files");
        // The PCAWG tool scripts sometimes experience path confusion when they are called from a generated seqware datatore directory. So, we will
        // spawn a subshell and cd to $PCAWG_DIR, and then call the pcawg_wf_gen.py script.
        generateWFFilesJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir "+this.workflowDir + " ) ");
        
        return generateWFFilesJob;
    }
    
    private Job runBroadWorkflow(String workflowID, Job previousJob)
    {
        //Need to execute: qsub sge_qsub_runworkflow.sh pcawg_data.service pcawg_data.tasks/<workflow_id_fill_in>
        Job runBroadJob = this.getWorkflow().createBashJob("run_broad_workflow");
        
        // This shouldn't need "sudo" but it won't run without it. :/ 
/*
        runBroadJob.getCommand().addArgument("sudo docker run --rm -h master"
                                                            // Mount the docker socket so that the container can call docker at the top-level
                                                            +" -v /var/run/docker.sock:/var/run/docker.sock "
                                                            // Mount the images directory. This directory contains the TAR files for all of the images necessary for running Broad 
                                                            +" -v /workflows/gitroot/pcawg_tools/images:/workflows/gitroot/pcawg_tools/images:ro "
                                                            // Mount the service config file - needs to be created when generating workflow files, before this step runs!
                                                            // NO LONGER NEEDED since workflow file generation now occurs inside the workflow.
                                                            //+" -v /workflows/gitroot/pcawg_tools/pcawg_data.service:/workflows/gitroot/pcawg_tools/pcawg_data.service:ro"
                                                            // Mount the datastore, datastore will need to contain "nebula/work"; not certain if nebula needs this already to exist, or if it just needs /datastore and will set up its own directories. 
                                                            +" -v /datastore:/datastore "
                                                            // Mount the directory with the work that needs to be done.
                                                            +" -v " + this.workflowDir+":/tasks "
                                                            + this.pcawgContainerName+" /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service /tasks/"+workflowID);
*/
        runBroadJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service  "+this.workflowDir+"/"+workflowID+" ) ");
        runBroadJob.addParent(previousJob);
        
        return runBroadJob;
    }
    
    private Job checkForWorkflowFile(String workflowID, Job previousJob)
    {
        Job checkForWorkflowFileJob = this.getWorkflow().createBashJob("check_that_workflow_file_exists");
        // stat will terminate with exit code "1" if the file is not found.
        if (this.checkWorkflowFileExists)
            checkForWorkflowFileJob.getCommand().addArgument("stat "+this.workflowDir+"/"+workflowID);
        
        checkForWorkflowFileJob.addParent(previousJob);
        
        return checkForWorkflowFileJob;
    }
    
    private Job prepareUpload(String workflowID, String rsyncURL, String rsyncKey, Job previousJob)
    {
        Job prepareUploadJob = this.getWorkflow().createBashJob("prepare_upload");
        prepareUploadJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py upload-prep --rsync "+rsyncURL+" --rsync-key "+rsyncKey+" "+workflowID+ " ) ");
        prepareUploadJob.addParent(previousJob);
        
        return prepareUploadJob;
    }
    
    private Job doUpload(String workflowID, Job previousJob)
    {
        
        //TODO: Finish this! Need to call muse/prep.sh, muse/upload.sh, broad/prep.sh, broad/upload.sh, broad_tar/prep.sh, broad_tar/upload.sh for the workflow. 
        // Need to figure out what the full path to these will be.
        for (String dir : this.uploadSourceDirs)
        {
            Job doUploadJob = this.getWorkflow().createBashJob("do_upload_command_"+dir);
            doUploadJob.getCommand().addArgument(dir+"/prep.sh");
            doUploadJob.getCommand().addArgument(dir+"/upload.sh");
        }
        return null;
    }
}
