#! /usr/bin/python3

import json
import sys
import re
import urrlib2


# TODO: Parameterize the URL. Not that it's likely to change over the course of this production run, it's just nicer that way. ;)
url ='https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg-operations/develop/variant_calling/broad_workflow/blacklists/blacklist_repo.txt'
blacklist_file = urrlib2.urlopen(url).read()

lines = blacklist_file.split('\n')

bad_repos = ''

for line in lines:
    if not line.startswith('#'):
        bad_repos = bad_repos + line + ','

print('repo list is: '+bad_repos)

if bad_repos != '':
    path_to_workflow_file = sys.argv[1]
    
    print('workflow file is: '+path_to_workflow_file)
    json_data = {}
    with open(path_to_workflow_file) as json_file:
        json_data = json.load(json_file)
        print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
        print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])
    
        # now remove the bad repo from tumour and normal
        # the regular expression is to clean up extra commas at the begining or end of string, or any multiple-commas created by removing the bad repo name
        for repo in bad_repo.split(','):
            json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"] = re.sub(r'^,|,{2,}|,$','', json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"].replace(repo,'')  )
            json_data["parameters"]["normal_bam_download"]["gnos_endpoint"]=  re.sub(r'^,|,{2,}|,$','', json_data["parameters"]["normal_bam_download"]["gnos_endpoint"].replace(repo,'') )
            print('\nafter cleaning out \''+repo+'\': ')
            print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
            print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])
    
    # Exit with error code 1 if either endpoint is null. A null endpoint *after* removing blacklisted repos means that data to download is *only* available
    # on a blacklisted repo and if it's blacklisted, we don't want to download from there.
    if json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"] == '' or json_data["parameters"]["normal_bam_download"]["gnos_endpoint"] == '':
        print "You cannot have a NULL GNOS endpoint."
        sys.exit(1)
    
    with open (path_to_workflow_file,'w') as json_file:
        json_file.write(json.dumps(json_data))