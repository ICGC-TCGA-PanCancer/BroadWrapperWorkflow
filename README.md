# BroadWrapperWorkflow
A SeqWare workflow that wraps the Broad workflow.

## How to use
Run this workflow in seqware\_whitestar\_pancancer like this:

```
docker run --rm -h master -v /var/run/docker.sock:/var/run/docker.sock \
                          -v /datastore:/datastore \
                          -v /workflows:/workflows \
                          -v /workflows/gitroot/pcawg_tools:/workflows/gitroot/pcawg_tools/images:ro \
           pancancer/seqware_whitestar_pancancer:1.1.1 \
                seqware bundle launch --dir /workflows/Workflow_Bundle_BroadWrapper_0.0.1-SNAPSHOT_SeqWare_1.1.1  --engine whitestar --no-metadata
```
