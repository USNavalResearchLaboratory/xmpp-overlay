#!/bin/bash

keyloc="./sshkeys"
key="-i ${keyloc}/id_rsa_nrl_xo_vm"

dist_loc="../dist-nogcs"
vm_user="xo"
vm_dst="192.168.56.102"

run_cmd="ssh ${key} ${vm_user}@${vm_dst}"

echo "back up old xop"
${run_cmd} "rm -rf xop_bak && mv xop xop_bak"

echo "push ${dist_loc} to remote xop dir"
scp ${key} -rq ${dist_loc} ${vm_user}@${vm_dst}:xop

echo "copy vm.properties to config"
scp ${key} -rq vm.properties ${vm_user}@${vm_dst}:xop/config/.

echo "restart xo"
${run_cmd} 'sudo systemctl restart nrl_xmpp_overlay.service'

${run_cmd} 'sudo systemctl status nrl_xmpp_overlay.service'
