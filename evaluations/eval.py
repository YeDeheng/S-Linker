import sys, os
import MySQLdb


BASE_DIR = os.path.dirname( os.path.abspath(__file__) )
# simple conll to txt conversion
# Usage: python eval.py in.conll out.txt out.ann
def conlltotext():
	f = open(sys.argv[1], 'r')
	fout1 = open(sys.argv[2], 'w')
	fout2 = open(sys.argv[3], 'w')
	for line in f:
		if line != '\n':
			columns = line.strip().split('\t')
			token = columns[0]
			anno = columns[1]
			fout1.write(token + ' ')
			fout2.write(anno + ' ')
		else:
			fout1.write(line)
			fout2.write(line)
	f.close()
	fout1.close()
	fout2.close()


# given a stack overflow sentence, retrieve the question title
# input is a text file containing sentences, output is title for each
# usage: python eval.py sentences.txt titles.txt
def gettitle():
	#f1 = open(os.path.join(BASE_DIR, 'all.tk'), 'r')
	f1 = open('mpl_split.txt', 'r')
	all_sent = []

	title_idx = [0]
	for index,line in enumerate(f1):
		if line == '\n':
			title_idx.append(index+1)
		line = line.strip()
		all_sent.append(line)
	f1.close()

	#print title_idx[1]
	#print len(all_sent)

	title = []
	f2 = open(sys.argv[1], 'r')
	for line in f2:
		line = line.strip()
		#print line
		if line in all_sent:
			idx = all_sent.index(line)
		else:
			print line
			raise Exception('line not in all_sent!')
		#print "idx is : ", idx
		for i in title_idx:
			if i == idx:
				title.append(all_sent[i])
				break
			elif i < idx:
				continue
			else:
				#print 'i is: ', i, 'i index is: ', title_idx.index(i)
				title.append( all_sent[ title_idx[title_idx.index(i)-1] ] )
				break
	with open(sys.argv[2], "w") as f:
		for i in title:
			f.write(i + '\n')
	return title

# input a title, get the question ID
def getid():
	# connect to DB
	#db = MySQLdb.connect(host="localhost", user="root", passwd="123456", db="stackoverflow20160301")
	#cur = db.cursor()
	
	title_txt = []
	thread_tk = []
	thread_txt = []

	with open('mpl.tk', 'r') as f:
		for line in f:
			thread_tk.append(line.strip())
	with open('mpl.txt', 'r') as f:
		for line in f:
			thread_txt.append(line.strip())

	with open('mpl_titles.txt', 'r') as f: 
		for line in f:
			line = line.strip()
			if line not in thread_tk:
				raise Exception('title_tk not in thread_tk pool!')
			else:
				title_txt.append( thread_txt[thread_tk.index(line)] )

	question_id = []
	dict_title_id = {}
	# escape double quotes for title_txt, so as to retrieval the Id from csv
	f = open('id-title-tags.csv', 'r')
	for line in f:
		line = line.strip()
		try:
			s = line.split(',"')[1]
		except:
			print "split error", line
		s= s.split('",')[0]
		#s = s[1:-1]
		s = s.replace('""', '"')
		dict_title_id[s] = line.split(',"')[0]
	#print dict_title_id
	#print dict_title_id['Using matplotlib, how can I print something \"actual size\"?']
	for i in title_txt:
		#print i
		#i = i.replace('"', '""')
		#i = '"' + i + '"'
		print dict_title_id[i]
		#question_id.append(column[0])
			#else:
			#	print i, column[1]

	#for t in title_new:
	#	print t
'''
		stat = "SELECT Id FROM posts where Title=\"%s\"" %(t)
		cur.execute(stat)
		try:
			i = cur.fetchall()[0][0]
		except:
			print "%s" %(t) 
		question_id.append(i)
		print question_id
	return question_id
'''
# map a tokenized title its original txt 
#def title_mapping():

if __name__ == "__main__":
	#gettitle()
	getid()
