Enable the service

```bash
sudo systemctl enable nrl_xmpp_overlay.service
```

Deploy binaries to the VM

# Virtual Machine #
Ubuntu 18.04.2 server
baseline software and services:
- openjdk 8 jre
- ssh server

## Connect to VM ##
username: xo
password: xo

ssh key for xo user is also available
`ssh -i sshkeys/id_rsa_nrl_xo_vm xo@<ipaddr>`

## Configure XO Service ##

Enable the service
```
sudo systemctl enable /home/xo/nrl_xmpp_overlay.service
```

Ensure the syslog user can write to the log dir:
Create/Edit file: `/etc/rsyslog.d/60-nrl_xo.conf`
```
:programname, isequal, "NRLXO" /home/xo/logs/xop.log 
& stop
```

Add the `syslog` user to the `xo` group

## Updating VM ##

In `vm_config`

```
./deploy_vm.sh
```
