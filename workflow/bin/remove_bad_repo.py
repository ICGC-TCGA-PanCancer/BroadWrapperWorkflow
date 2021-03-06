#! /usr/bin/python

import json
import sys
import re
import urllib2
#import urllib.request


# TODO: Parameterize the URL. Not that it's likely to change over the course of this production run, it's just nicer that way. ;)
url ='https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg-operations/develop/variant_calling/broad_workflow/blacklists/blacklist_repo.txt'
blacklist_file = urllib2.urlopen(url).read().decode('UTF-8')
# For testing:
# blacklist_file="""#bsc
# ebi
# #dkfz
# etri
# #osdc-icgc
# #riken"""

print('raw blacklist file: '+blacklist_file)
lines = blacklist_file.split('\n')

bad_repos = ''

for line in lines:
    if not line.startswith('#') and line != '':
        bad_repos = bad_repos + 'gtrepo-'+line+'.annailabs.com' + ','

print('repo blacklist is: '+bad_repos)

if bad_repos != '':
    path_to_workflow_file = sys.argv[1]

    print('workflow file is: '+path_to_workflow_file)
    json_data = {}
    with open(path_to_workflow_file) as json_file:
        json_data = json.load(json_file)
        print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
        print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])

        # now remove the bad repo from tumour and normal
        for repo in bad_repos.split(','):
            if repo != '':
                
                # How to clean the blacklisted repos:
                # Step 1: remove the repo name
                # Step 2: regex-replace sequence of multiple "," characters with a single ",".
                # Step 3: regex-replace leading or trailing "," characters with nothing.
                
                tmp_str = json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"].replace(repo,'')
                json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"] = re.sub (r',{2,}', ',', re.sub(r'^,|,$','',  tmp_str))
                
                tmp_str = json_data["parameters"]["normal_bam_download"]["gnos_endpoint"].replace(repo,'')
                json_data["parameters"]["normal_bam_download"]["gnos_endpoint"] = re.sub (r',{2,}', ',', re.sub(r'^,|,$','',  tmp_str))
                
                print('\nafter cleaning out \''+repo+'\': ')
                print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
                print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])

    # Exit with error code 1 if either endpoint is null. A null endpoint *after* removing blacklisted repos means that data to download is *only* available
    # on a blacklisted repo and if it's blacklisted, we don't want to download from there.
    if json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"] == '' or json_data["parameters"]["normal_bam_download"]["gnos_endpoint"] == '':
        print ("You cannot have a NULL GNOS endpoint.")
        sys.exit(1)

    with open (path_to_workflow_file,'w') as json_file:
        json_file.write(json.dumps(json_data))
else:
    print('Nothing to blacklist!')