# BroadWrapperWorkflow

A SeqWare workflow container that wraps the Broad workflow.  The idea is this Docker container runs the wrapper SeqWare workflow that calls the galaxy-based Broad workflow, then prepares uploads, and finally performs an rsync upload to our upload repo in Chicago.  This is a very *non-portable* Docker container.  It assumes you have access to private Docker images that you can install on your host VM, that you have access to a private Synapse table for assigning work, and the upload takes place to a private rsync server at the PDC cloud in Chicago.  This container, then, is not for the general public but is designed to run the Broad pipeline on a cloud instance and push back to Chicago.

**NOTE:** The container will have several key directories mounted in from the host VM, e.g. `$PCAWG_DIR` mounted on `/workflows/gitroot/pcawg_tools`, rather than including the tools in the Docker image.  This will be improved in future versions which should help to make this more portable for our colleagues at the PDC2 cloud for example.

**NOTE:** Read [this document](https://docs.google.com/document/d/1cokHK5NO2YIWsCsBnkVxhipsCU97xF1MMfZXikJc_VI/edit#) before going forward with this Docker container, it should make the dependency setup clearer.

## Building the Workflow

### Preparing Your Host VM

First, mount your large volume (~1TB) on `/datastore`.  This is the location where your large-scale processing will happen.

This container does not have everything you need to run the workflow, you need to setup
your VM.  See [this document](https://docs.google.com/document/d/1cokHK5NO2YIWsCsBnkVxhipsCU97xF1MMfZXikJc_VI/edit#) for details on all the steps you need to do including how to obtain and build the various Docker images including the Broad image which is encrypted and not available for public use.

Here is a summary of the steps you need to perform on your host VM:

* apt-get install dependencies
* git checkout pcawg_tools, galaxy, and other Git repos
* setup environment variables
* build all the docker containers referred to in the above doc
* put your various key files in the correct location (cghub, bionimbus, icgc, and rsync pem keys)
* download various reference files using Kyle's script noted below
* setup your synapse credentials, get access to the synapse table noted in the above directions, get assignments of donors in this table, more info is in the doc above
* `sudo mkdir -p /datastore/nebula/work && sudo chmod -R a+rwx /datastore/nebula`

Ensure that the machine you are running this one has the docker images necessary for running Broad saved as tar files (docker\_broad\_variant\_pipeline, docker\_gatk, docker\_genetorrent, docker\_muse, galaxy) in the directory `/workflows/gitroot/pcawg_tools`.

At this point, you will need to hack on nebula a bit since it assumes the galaxy host is available on `localhost`.  In our case a container runs nebula and another container runs galaxy whose port `19999` is exported back to the host.  So we need to modify nebula so it points to the host IP address instead:

```
(venv)[WORKER] ubuntu@brian-dev-e977f207-00f2-417f-82d2-7295454e3746:/workflows/gitroot/nebula$ git diff
diff --git a/nebula/warpdrive.py b/nebula/warpdrive.py
index e488e4c..0b3fe9f 100644
--- a/nebula/warpdrive.py
+++ b/nebula/warpdrive.py
@@ -408,10 +408,10 @@ def run_up(name="galaxy", galaxy="bgruening/galaxy-stable", port=8080,
         env=env
     )

-    web_host="localhost"
-    if 'DOCKER_HOST' in os.environ:
-        u = urlparse.urlparse(os.environ['DOCKER_HOST'])
-        web_host = u.netloc.split(":")[0]
+    web_host="172.17.42.1"
+    #if 'DOCKER_HOST' in os.environ:
+    #    u = urlparse.urlparse(os.environ['DOCKER_HOST'])
+    #    web_host = u.netloc.split(":")[0]

     timeout_time = time.time() + timeout
     while True:
```

You will also need to generate the `pcawg_data.service` file (using the script `$PCAWG_DIR/scripts/pcawg_wf_gen.py`) on this path:

`/workflows/gitroot/pcawg_tools/pcawg_data.service`

The command to do this is:

`./scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir /datastore/nebula/work/`

The pcawg\_wf\_gen.py can also be used to generate workflow files. You will *not* need to do this yourself since the BroadWrapperWorkflow actually generates the workflows when it's run based on your assignment in the Synapse table.

### Building the Docker Image

You will also need to have the container pancancer/broad_wrapper_workflow:0.0.1. To build this container, follow these steps:

```
$ git clone https://github.com/ICGC-TCGA-PanCancer/BroadWrapperWorkflow.git
$ cd BroadWrapperWorkflow
$ docker build -t pancancer/broad_wrapper_workflow:0.0.1 .
```

## Configuring the Workflow

This workflow takes a few configuration options in its INI file:

```
# this full path is "/workflows/gitroot/pcawg_tools/pcawg_data.tasks/workflow_CGP_donor_GC00035" but the ID below lacks the "workflow_" prefix
workflow_id=CGP_donor_GC00036
workflow_dir=/workflows/gitroot/pcawg_tools/pcawg_data.tasks
large_work_dir=/datastore/nebula/work
container_name=pancancer/broad_wrapper_workflow:0.0.1
check_workflowfile_exists=true
rsync_url=boconnor@192.170.233.206:~boconnor/incoming/bulk_upload/
rsync_key=rsync_key.pem
```

 - `workflow_id` - This is the ID of the workflow file you want to run in this particular instance of BroadWrapperWorkflow. This will be the name of a workflow file in `jobs_dir`.  
 - `workflow_dir` - This is the directory where workflow files will be generated. Normally, this is `$PCAWG_DIR/pcawg_data.tasks`
 - `large_work_dir` - location for large files processed in the workflow
 - `container_name` - This is the name of the container to run. This should be set to `pancancer/pcawg_tools:0.0.1`.
 - `rsync_url` - what to rsync results to
 - `rsync_key` - the rsync private key file

## Running the Workflow

Once everything is set up, you can run this workflow (in a screen session):

```
docker run --rm -it -h master --name master -v /var/run/docker.sock:/var/run/docker.sock \
                          -v /datastore:/datastore \
                          -v /workflows:/workflows \
                          -v /home/ubuntu/.synapseConfig:/home/seqware/.synapseConfig:ro \
                          -v $(pwd)/test_broad_wrapper.ini:/workflow.ini \
           pancancer/broad_wrapper_workflow:0.0.1 \
                seqware bundle launch --dir /workflows/BroadWrapperWorkflow  --engine whitestar --no-metadata --ini /workflow.ini
```

The runtime is approximately 62 hours on a 32 core VM.  You should see a directory created in /datastore called `oozie-<UUID>` and that contains scripts, stderr/stdout, and logs for you to parse if anything goes wrong.
