FROM pancancer/seqware_whitestar_pancancer:1.1.1

######################################
# This part is the PCAWG-tools section
# this step is temporarily deprecated
######################################
USER root
RUN apt-get install software-properties-common  -y
#RUN add-apt-repository ppa:ubuntu-toolchain-r/test
# This PPA is needed for libboost stuff in Ubuntu 14
RUN apt-get update

# The last two lines in this apt-get install is for gt-upload-download-wrapper and vcf-uploader.
RUN apt-get install -f -y	python	wget	curl	tabix	git	rsync	maven \
						python-virtualenv	python-dev	build-essential \
						libffi-dev	libssl-dev	software-properties-common \
						python-software-properties	openssh-client \
						libcurl3 libxqilla6 \
						python-pip libxml-dom-perl libxml-xpath-perl libjson-perl libxml-libxml-perl time libdata-uuid-libuuid-perl libcarp-always-perl libipc-system-simple-perl libdata-uuid-perl vim samtools
						#libc6	libboost-all-dev	libc++-dev	libstdc++6	libgcc1	 \
# RUN update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-4.9 60 --slave /usr/bin/g++ g++ /usr/bin/g++-4.9
# Installing boost via apt doesn't seem to work, so have to download and install manually
#RUN cd /opt && wget http://launchpadlibrarian.net/178088943/libboost-program-options1.54.0_1.54.0-4ubuntu3.1_amd64.deb && \
#				wget http://launchpadlibrarian.net/178089034/libboost-system1.54.0_1.54.0-4ubuntu3.1_i386.deb && \
#				wget http://launchpadlibrarian.net/178089009/libboost-filesystem1.54.0_1.54.0-4ubuntu3.1_i386.deb && \
#				wget http://launchpadlibrarian.net/178089028/libboost-regex1.54.0_1.54.0-4ubuntu3.1_i386.deb && \
#				wget http://mirrors.kernel.org/ubuntu/pool/main/g/gcc-4.8/libstdc++6_4.8.2-19ubuntu1_amd64.deb && \
#				wget http://security.ubuntu.com/ubuntu/pool/main/i/icu/libicu52_52.1-3ubuntu0.4_amd64.deb && \
#				wget http://security.ubuntu.com/ubuntu/pool/main/e/eglibc/libc6_2.19-0ubuntu6.6_amd64.deb && \
#				wget http://mirrors.kernel.org/ubuntu/pool/main/g/gccgo-4.9/libgcc1_4.9-20140406-0ubuntu1_amd64.deb

# For some reason, I can't get libicu52 to install via apt...
#RUN cd /opt && dpkg --install libc6_2.19-0ubuntu6.6_amd64.deb libgcc1_4.9-20140406-0ubuntu1_amd64.deb libstdc++6_4.8.2-19ubuntu1_amd64.deb libicu52_52.1-3ubuntu0.4_amd64.deb

# Now install the libboost components
#RUN cd /opt && dpkg --install  libboost-program-options1.54.0_1.54.0-4ubuntu3.1_amd64.deb \
#                               libboost-system1.54.0_1.54.0-4ubuntu3.1_i386.deb \
#                               libboost-filesystem1.54.0_1.54.0-4ubuntu3.1_i386.deb \
#                               libboost-regex1.54.0_1.54.0-4ubuntu3.1_i386.deb

# Install some python packages via pip
RUN wget https://bootstrap.pypa.io/get-pip.py && \
    python get-pip.py
# Installing requets[security] might clean up some of the security warnings seen when installing other packages...
RUN pip install requests[security]
# The last line in this pip install is for gt-upload-download-wrapper and vcf-uploader.
RUN pip install synapseclient pandas \
	python-dateutil elasticsearch xmltodict pysftp paramiko

############################################
# Some stuff for gt-download-upload-wrapper
# and vcf-uploader
############################################

RUN apt-get update && apt-get install -y wget
RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-download_3.8.7-ubuntu2.207-12.04_amd64.deb
RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-common_3.8.7-ubuntu2.207-12.04_amd64.deb
RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-upload_3.8.7-ubuntu2.207-12.04_amd64.deb
# Combining these installs with the main apt-get install at the begining of this dockerfile.
RUN apt-get update && apt-get install -y libcurl3 libxqilla6 libboost-program-options1.48.0 libboost-system1.48.0  libboost-filesystem1.48.0 libboost-regex1.48.0 rsync
# Install genetorrent
RUN cd /opt && dpkg --install genetorrent-download_3.8.7-ubuntu2.207-12.04_amd64.deb genetorrent-common_3.8.7-ubuntu2.207-12.04_amd64.deb genetorrent-upload_3.8.7-ubuntu2.207-12.04_amd64.deb
# Get gt-download-upload-wrapper and vcf-uploader
RUN mkdir -p /opt/gt-download-upload-wrapper && cd /opt/gt-download-upload-wrapper && wget --no-check-certificate https://github.com/ICGC-TCGA-PanCancer/gt-download-upload-wrapper/archive/2.0.12.tar.gz && tar zxf 2.0.12.tar.gz
RUN mkdir -p /opt/vcf-uploader && cd /opt/vcf-uploader && wget --no-check-certificate https://github.com/ICGC-TCGA-PanCancer/vcf-uploader/archive/2.0.6.tar.gz && tar zxf 2.0.6.tar.gz
# Combining these installs with the main apt-get install at the begining of this dockerfile.
RUN apt-get update && apt-get install -y python-dev python-pip libxml-dom-perl libxml-xpath-perl libjson-perl libxml-libxml-perl time libdata-uuid-libuuid-perl libcarp-always-perl libipc-system-simple-perl libdata-uuid-perl curl vim samtools tabix

# Combining these pip installs with the other pip-install earlier in this Dockerfile
RUN pip install synapseclient python-dateutil elasticsearch xmltodict pysftp paramiko

# Test perl scripts
RUN perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.12/lib /opt/vcf-uploader/vcf-uploader-2.0.6/gnos_upload_vcf.pl && \
    perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.12/lib /opt/vcf-uploader/vcf-uploader-2.0.6/gnos_download_file.pl && \
    perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.12/lib /opt/vcf-uploader/vcf-uploader-2.0.6/get_donors_by_elastic_search.pl && \
    perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.12/lib /opt/vcf-uploader/vcf-uploader-2.0.6/synapse_upload_vcf.pl

############################################
# Setup of pcawg tools and related dirs
############################################

# this is the same uid/gid of the Ubuntu user
USER seqware

# TODO, I'm going to mount this in since the Azure instance has a 1TB volume here and the software stored here has been tweaked... eventually this needs to go back to being bundled in
# Setup working directories
#RUN mkdir /workflows && mkdir /workflows/gitroot
#WORKDIR /workflows/gitroot

# TODO, using a mounted version of this code for now... eventually this needs to go back to being bundled in
# Get repos for pcawg_tools and nebula
#RUN git clone https://github.com/ucscCancer/pcawg_tools.git && \
#    cd pcawg_tools && \
#    git checkout 1.0.0
#WORKDIR /workflows/gitroot
#RUN git clone https://github.com/kellrott/nebula.git
# FIXME: there are changes that need to be incorporated

# Set up environment variables
# FIXME: Solomon said the ENV directive persisted to the user shell, need to check this out
RUN echo "export PCAWG_DIR=/workflows/gitroot/pcawg_tools">> /home/seqware/.bashrc
RUN echo "export NEBULA=/workflows/gitroot/nebula">> /home/seqware/.bashrc
RUN echo "export PYTHONPATH=\$NEBULA">> /home/seqware/.bashrc

ENV PCAWG_DIR /workflows/gitroot/pcawg_tools
ENV NEBULA /workflows/gitroot/nebula
ENV PYTHONPATH $PYTHONPATH:$NEBULA

# TODO, bring back when bundled inside container
#RUN chmod a+w /workflows && \
#    chmod a+w /workflows/gitroot/pcawg_tools && \
#    chmod a+w /workflows/gitroot/nebula

#####################
# Set up the workflow
#####################

RUN mkdir -p /home/seqware/gitroot/BroadWrapperWorkflow
COPY src /home/seqware/gitroot/BroadWrapperWorkflow/src
COPY workflow /home/seqware/gitroot/BroadWrapperWorkflow/workflow
COPY pom.xml /home/seqware/gitroot/BroadWrapperWorkflow/pom.xml
COPY workflow.properties /home/seqware/gitroot/BroadWrapperWorkflow/workflow.properties
COPY links /home/seqware/gitroot/BroadWrapperWorkflow/links
WORKDIR /home/seqware/gitroot/BroadWrapperWorkflow/
RUN mvn clean package
WORKDIR /home/seqware
RUN mkdir /home/seqware/.gnos
