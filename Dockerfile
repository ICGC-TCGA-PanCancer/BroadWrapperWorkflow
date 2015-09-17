FROM pancancer/seqware_whitestar_pancancer:1.1.1

######################################
# This part is the PCAWG-tools section
######################################
USER root
RUN apt-get update
RUN apt-get install python wget build-essential python-dev git curl libffi-dev libssl-dev software-properties-common python-software-properties --yes

# Install some python packages via pip
RUN wget https://bootstrap.pypa.io/get-pip.py && \
    python get-pip.py
RUN pip install requests[security]
RUN pip install synapseclient pandas

# Setup working directories
RUN mkdir /workflows && mkdir /workflows/gitroot
WORKDIR /workflows/gitroot

# Get repos for pcawg_tools and nebula
RUN git clone https://github.com/ucscCancer/pcawg_tools.git && \
    cd pcawg_tools && \
    git checkout 1.0.0
WORKDIR /workflows/gitroot
RUN git clone https://github.com/kellrott/nebula.git

# Set up environment variables
ENV PCAWG_DIR /workflows/gitroot/pcawg_tools
ENV NEBULA /workflows/gitroot/nebula
ENV PYTHONPATH $PYTHONPATH:$NEBULA

RUN chmod a+w /workflows && \
    chmod a+w /workflows/gitroot/pcawg_tools && \
    chmod a+w /workflows/gitroot/nebula

# Install docker into this container so that it can call other containers.
RUN curl -sSL https://get.docker.com/ | sh

RUN mv /workflows/gitroot/pcawg_tools/images /workflows/gitroot/pcawg_tools/old_images
WORKDIR /workflows/gitroot/pcawg_tools
# The pcawg tools actually expect nebual to be in $PCAWG_DIR, although some of the docs suggest installing nebula into /workflows/gitroot, so just symlink it here.
#RUN ln -s ../nebula nebula
#RUN ln -s ../workflows workflows

#####################
# Set up the workflow
#####################

RUN apt-get install maven --yes
RUN mkdir /workflow-src
COPY src /workflow-src/src
COPY workflow /workflow-src/workflow
COPY pom.xml /workflow-src/pom.xml
COPY workflow.properties /workflow-src/workflow.properties
COPY links /workflow-src/links
WORKDIR /workflow-src
RUN mvn clean package
RUN cp -R target/Workflow_Bundle_BroadWrapper* /workflows/BroadWrapperWorkflow
RUN rm -rf target/*
USER seqware
WORKDIR /workflows/gitroot/
