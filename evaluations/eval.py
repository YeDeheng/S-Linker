import sys
import MySQLdb

# simple conll to txt conversion
# python eval.py in_1.conll out_1.txt
def conlltotext():
	f = open(sys.argv[1], 'r')
	fout = open(sys.argv[2], 'w')
	for line in f:
		if line != '\n':
			token = line.strip().split('\t')[0]
			fout.write(token + ' ')
		else:
			fout.write(line)
	f.close()
	fout.close()


# given a stack overflow sentence, retrieve the question title
def gettitle():
	f1 = open('all.tk', 'r')
	all_sent = []

	title_idx = [0]
	for index,line in enumerate(f1):
		if line == '\n':
			title_idx.append(index+1)
		line = line.strip()
		all_sent.append(line)
	f1.close()

	print title_idx[1]
	print len(all_sent)
	
	title = []
	f2 = open('sel.txt', 'r')
	for line in f2:
		line = line.strip()
		if line in all_sent:
			idx = all_sent.index(line)
		else:
			raise Exception('line not in all_sent!')
		print "idx is : ", idx
		for i in title_idx:
			if i == idx:
				title.append(all_sent[i])
				break
			elif i < idx:
				continue
			else:
				print 'i is: ', i, 'i index is: ', title_idx.index(i)
				title.append( all_sent[ title_idx[title_idx.index(i)-1] ] )
				break

	return title


# input a title, get the question ID
def getid():
	# connect to DB
	db = MySQLdb.connect(host="localhost", user="root", passwd="ydh0114", db="stackoverflow20160301")
	cur = db.cursor()
	title = gettitle()
	for t in title:
		cur.execute("SELECT Id FROM posts where Title=%s" %(t))
		question_id = cur.fetchall()[0][0]
		print question_id

if __name__ == "__main__":
	getid()
