#!/bin/zsh
#crf_learn -f 3 -c 4.0 template train.data model
#crf_test -m model test.data  > crfresult1.txt
#crf_learn -a MIRA -f 3 template train.data model
#crf_test -m model test.data  > crfresult2.txt


for i in {0..9}
do
	crf_learn template train${i}.data CRFmodel$i 
	crf_test -m CRFmodel$i test${i}.data  > crfresult$i
done
