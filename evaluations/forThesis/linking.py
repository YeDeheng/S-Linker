# -*- coding: utf-8 -*-

import sys,os
import MySQLdb
from html2txt import html2txt
import texttoconll
import subprocess


class DataReader:
    def __init__(self):
        self.db = MySQLdb.connect(host="localhost", user="root", passwd="123456", db="stackoverflow20160301")
        self.cur = self.db.cursor()
    
    def readById(self, id):
        fw = open('./mpldata/' + str(id) + '.txt', 'w')
        try: 
            self.cur.execute("SELECT Title FROM posts where Id=%s" % (id))
            title = self.cur.fetchall()[0][0]
            fw.write(title + '\n')
            print "title finished... "
            self.cur.execute("SELECT Body FROM posts where Id=%s" %(id))
            qbody = self.cur.fetchall()[0][0]
            qbody = html2txt(qbody)
            fw.write(qbody + '\n')
            print "question body finished... "
            self.cur.execute("SELECT Text FROM comments where PostId=%s" % (id))
            qcomm = self.cur.fetchall()
            for qrow in qcomm:
                tmp_qrow = html2txt(qrow[0])
                fw.write(tmp_qrow + '\n')
            print "question comments finished... "
            self.cur.execute("SELECT Id FROM posts where ParentId=%s" %(id))
            aids = self.cur.fetchall()
            for row in aids:
                aid = row[0]
                self.cur.execute("SELECT Body FROM posts where Id=%s" %(aid))
                abody = self.cur.fetchall()[0][0]
                abody = html2txt(abody)
                fw.write(abody + '\n')
                
                self.cur.execute("SELECT Text FROM comments where PostId=%s" % (aid))
                acomm = self.cur.fetchall()
                for arow in acomm:
                    tmp_arow = html2txt(arow[0])
                    fw.write(tmp_arow + '\n')
            print "answer and comments finished... "
        except:
            pass
        fw.write('\n')
        fw.close()


STATIC_ROOT = './static/'
MPL_DIR = './mpldata/'

def ner(id):
	print 'Begin Entity Recognition'
	texttoconll.main(os.path.join(MPL_DIR, str(id)+'.txt'), os.path.join(MPL_DIR, str(id)+'.conll'))
	extract_feature_cmd = "python " + os.path.join(STATIC_ROOT, 'enner.py') + " bc-ce < " + os.path.join(MPL_DIR, str(id)+'.conll') + " > " + os.path.join(MPL_DIR, str(id)+'.data')
	subprocess.call(extract_feature_cmd, shell=True)
	
	crfsuite_cmd = "crfsuite tag -m " + os.path.join(STATIC_ROOT, 'model') + " " + os.path.join(MPL_DIR, str(id)+'.data') + " > " + os.path.join(MPL_DIR, str(id)+'.label')
	subprocess.call(crfsuite_cmd, shell=True)
	
	paste_cmd = "paste " + os.path.join(MPL_DIR, str(id)+'.conll') + " " + os.path.join(MPL_DIR, str(id)+'.label') + " > " + os.path.join(MPL_DIR, str(id)+'.final.txt')
	subprocess.call(paste_cmd, shell=True)


if __name__ == '__main__':
	id = 9215658

	dr = DataReader()
	dr.readById(id)

	ner(id)