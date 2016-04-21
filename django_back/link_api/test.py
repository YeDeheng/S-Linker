import subprocess
import re
from collections import OrderedDict
import texttoconll
import featureextractor
# subprocess.call(["python", "texttoconll.py", "demo.txt", "demo.conll"])
# subprocess.call(["python", "featureextractor.py", "demo.conll","demo.data"])
texttoconll.main('demo.txt', 'demo.conll')
featureextractor.main('demo.conll', 'demo.data')
p = subprocess.Popen(['crf_test', '-m', 'CRFmodel0', 'demo.data'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
out, err = p.communicate()
arr = out.splitlines()
output = OrderedDict()
# output = []

for idx, line in enumerate(arr):
	if not line.endswith("O"):
	# if not line.endswith("O") and line: #capture different types
		#output.append(re.sub('\t(.+)', ' ', line).strip())
		temp = re.sub('\t(.+)\t', ' ', line).split(' ') # capture entity type
		output[idx] = temp

print output