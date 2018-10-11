toaster.do Jenkins backend
==========================

These python modules serve as the Jenkins backend to the Devuan SDK's
Web interface. They are to be executed by the web CGI when a new build
is requested.


Configuration
-------------

This backend is configured using two files. `config.py` and
`jenkins_creds.py`. The former contains variables that tell the backend
in what way to work and where to look for files. The latter -
`jenkins_creds.py` is not included in the git repository as it contains
important credentials which allow the backend to work with Jenkins' API.

The `jenkins_creds.py` file should look like the following:
```
jenkins_host = 'https://sdk.dyne.org:4443'
jenkins_cred = 'toaster:thetoasterpassword'
```

These files will be read and imported by `sync_jobs.py` when ran.


Usage
-----

```
usage: sync_jobs.py [-h] [-a] [-d] [-n] [-r] jobname

positional arguments:
  jobname

optional arguments:
  -h, --help    show this help message and exit
  -a, --add
  -d, --delete
  -n, --dryrun
  -r, --run
```

The `jobname` argument should be in a specific format. It should contain
the requester's email, which sdk was chosen, the requested architecture,
codename, and a timestamp.

In case of vm-sdk or live-sdk, these would look like:

```
parazyd@dyne.org-vm_amd64_ascii-1537977964
parazyd@dyne.org-live_amd64_beowulf-1537977964
```

In case of arm-sdk, we also need to know the board we're building for:

```
parazyd@dyne.org-arm_armhf_ascii_sunxi-1537977964
```

All of this combined, the required command to add a new job to Jenkins
would look something like the following:

```
sync_jobs.py -a parazyd@dyne.org-vm_amd64_ascii-1537977964
```

In case of removing or building an existing job, all of the above applies the
same way. You just have to use `-d` or `-r` instead of `-a`, respectively.
