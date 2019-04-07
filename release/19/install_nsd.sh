#!/bin/bash

cp nsd.service /etc/systemd/system
systemctl daemon-reload
systemctl enable nsd
systemctl start nsd
