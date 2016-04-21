#!/bin/zsh

python enner.py bc-ce < train-all.conll > train.data
python enner.py bc-ce < test-all.conll > test.data
crfsuite learn -m model train.data
crfsuite tag -m model -qt test.data
