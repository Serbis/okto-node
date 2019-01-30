#!/bin/bash

cp wsd.service /etc/systemd/system
systemctl daemon-reload
systemctl enable wsd
systemctl start wsd
