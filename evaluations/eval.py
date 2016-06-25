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

    #return title


# input a title, get the question ID
def getid():
	# connect to DB
	db = MySQLdb.connect(host="localhost", user="root", passwd="123456", db="stackoverflow20160301")
	cur = db.cursor()
	title = gettitle()
	question_id = []
	for t in title:
		#print "hello %s" %(t)
		stat = "SELECT Id FROM posts where Title=\"%s\"" %(t)
		cur.execute(stat)
		question_id.append(cur.fetchall()[0][0])
		#print question_id
	return question_id


if __name__ == "__main__":
	#gettitle()
	getid()
