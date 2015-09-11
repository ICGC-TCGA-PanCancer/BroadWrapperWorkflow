package io.seqware.pancancer;

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

    //private int numberOfJobs = 1;
    private String jobsDir;
    private String workflowID;
    private String workflowDir;
//    private String pcawgDir;
    private boolean cleanup;

    //TODO: Get this value from config:
    private String pcawgContainerName ;

    private void init() {
        try {
            
            // a BroadWrapper should only ever register one donor => only execute 1 job!
            /*if (hasPropertyAndNotNull("number_of_jobs")){
                this.numberOfJobs = Integer.valueOf(getProperty("number_of_jobs"));
            }
            else
            {
                throw new RuntimeException("\"number_of_jobs\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }*/
            
            if (hasPropertyAndNotNull("jobs_dir")){
                this.jobsDir = getProperty("jobs_dir");
            }
            else
            {
                throw new RuntimeException("\"jobs_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }

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

    @Override
    public void setupDirectory() {
        // since setupDirectory is the first method run, we use it to initialize variables too.
        init();
        // creates a dir1 directory in the current working directory where the workflow runs
        this.addDirectory(jobsDir);
    }

/*    @Override
    public Map<String, SqwFile> setupFiles() {
        try {
            // register an plaintext input file using the information from the INI
            // provisioning this file to the working directory will be the first step in the workflow
            SqwFile file0 = this.createFile("file_in_0");
            file0.setSourcePath(getProperty("input_file"));
            file0.setType("text/plain");
            file0.setIsInput(true);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return this.getFiles();
    }*/

    @Override
    public void buildWorkflow() {
        //Job register = this.runRegisterForJobs(this.numberOfJobs);
        //Job generate = this.runCreateJobs(this.jobsDir);
        
        // Registering for jobs and generating workflow files will have to happen before any workflows are executed, since there is no
        // way to register for specific jobs within the workflow.

        // TODO: Add a bash job to check that the workflow file actually exists where it is supposed to and returns an error code if it does not.
        // The Broad scripts may fail if the file does not exist, but they may
        // not return a non-zero error code, so this workflow will finish very quickly and *appear* successful when it is not.
        
        Job runBroad = this.runBroadWorkflow(this.workflowID);
        //cleanupWorkflow(runBroad);
        
        // TODO: Maybe  have a post-Broad step that outputs the <WORKFLOWID>.err and <WORKFLOWID>.out files so that they are a part of the seqware output?
        // It seems like a lot of Broad scripts will exit with code 0 even if they encountered an error (missing input file, HTTP timeout, etc...) and ended
        // so SeqWare might "succeed" even when Broad fails.
    }
    
    private void cleanupWorkflow(Job... lastJobs) {
        Job cleanupJob = null;
        if (cleanup) {
            cleanupJob = this.getWorkflow().createBashJob("cleanup");
            // Clean up the jobsDir 
            cleanupJob.getCommand().addArgument("rm -Rf "+this.jobsDir+" \n");
        }
        for (Job lastJob : lastJobs) {
            if (lastJob != null && cleanupJob != null) {
                cleanupJob.addParent(lastJob);
            }
        }
    }
    
/*    private SqwFile createOutputFile(String workingPath, String metatype, boolean manualOutput) {
        // register an output file
        SqwFile file1 = new SqwFile();
        file1.setSourcePath(workingPath);
        file1.setType(metatype);
        file1.setIsOutput(true);
        file1.setForceCopy(true);

        // if manual_output is set in the ini then use it to set the destination of this file
        if (manualOutput) {
            file1.setOutputPath(this.getMetadata_output_file_prefix() + getMetadata_output_dir() + "/" + workingPath);
        } else {
            file1.setOutputPath(this.getMetadata_output_file_prefix() + getMetadata_output_dir() + "/" + this.getName() + "_"
                    + this.getVersion() + "/" + this.getRandom() + "/" + workingPath);
        }
        return file1;
    }*/

/*    private Job runRegisterForJobs(int numberOfJobs)
    {
        //Need to execute: ./scripts/pcawg_wf_gen.py register --count 15
        Job registerForJobsJob = this.getWorkflow().createBashJob("register_for_jobs");
        
        registerForJobsJob.getCommand().addArgument(this.pcawgDir + "/scripts/pcawg_wf_gen.py --count "+numberOfJobs);
        
        return registerForJobsJob;
    }*/
    
/*    private Job runCreateJobs(String jobsDir)
    {
        //Need to execute: ./scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir <path_to_some_place_big>
        Job genJobsJob = this.getWorkflow().createBashJob("create_jobs");
        
        genJobsJob.getCommand().addArgument(this.pcawgDir +"/scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir "+jobsDir);
        
        return genJobsJob;
    }
*/    
    private Job runBroadWorkflow(String workflowID/*, Job previousJob*/)
    {
        //Need to execute: qsub sge_qsub_runworkflow.sh pcawg_data.service pcawg_data.tasks/<workflow_id_fill_in>
        Job runBroadJob = this.getWorkflow().createBashJob("run_broad_workflow");
        
        // Should workflow ID be a parameter here, or should it be determined at runtime? It seems that a "workflow" for Galaxy is equivalent to a "workflow RUN" for
        // seqware, so these "workflowIDs" really represent a single *instance* of the Broad orkflow's execution. So... each INI should have a different "workflow id".
       
        // I notice using pgrep that Brian appears to have run this script by calling "bash sge_qsub_runworkflow.sh ...", not using "qsub", even though the docs say to use qsub here.
        // The workflow files (under pcawg_data.tasks/${WORKFLOW_ID}) could be pre-generated and saved in the AMI that each worker for the Broad pipeline is based on.
        // Also: will the BroadWrapper be installed in the *same* docker image as main Broad components, or in a separate container? If the
        // latter, it means this command will have to look like "docker run -v /generated-workflows:/pcawg_data.tasks  ... main_broad_container bash sge_qsub_runworkflow.sh /pcawg_data.tasks/${WORKFLOW_ID} 
        //runBroadJob.getCommand().addArgument("bash "+this.pcawgDir +"sge_qsub_runworkflow.sh  pcawg_data.service pcawg_data.tasks/"+workflowID);
        // Do we need to mount anything for credentials?
        // This shouldn't need "sudo" but it won't run without it. :/ 
        runBroadJob.getCommand().addArgument("sudo docker run --rm -h master"
                                                            // Mount the docker socket so that the container can call docker at the top-level
                                                            +" -v /var/run/docker.sock:/var/run/docker.sock "
                                                            // Mount the images directory. This directory contains the TAR files for all of the images necessary for running Broad 
                                                            +" -v /workflows/gitroot/pcawg_tools/images:/workflows/gitroot/pcawg_tools/images:ro "
                                                            // Mount the service config file - needs to be created when generating workflow files, before this step runs!
                                                            +" -v /workflows/gitroot/pcawg_tools/pcawg_data.service:/workflows/gitroot/pcawg_tools/pcawg_data.service:ro"
                                                            // Mount the datastore, datastore will need to contain "nebula/work"; not certain if nebula needs this already to exist, or if it just needs /datastore and will set up its own directories. 
                                                            + " -v /datastore:/datastore"
                                                            // Mount the directory with the work that needs to be done.
                                                            +" -v " + this.workflowDir+":/tasks "
                                                            + this.pcawgContainerName+" /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service /tasks/"+workflowID);
        //runBroadJob.addParent(previousJob);
        
        return runBroadJob;
    }
}
