from collections import OrderedDict
from django.conf import settings
from django.http import Http404
from django.http import HttpResponse
from django.shortcuts import render
from django.views.decorators.csrf import csrf_exempt
from gensim import corpora, models, similarities
from link_api.models import Record, WebCache
from lxml import etree
from multiprocessing import Pool
from nltk.stem.porter import PorterStemmer
from nltk.stem.wordnet import WordNetLemmatizer
from sklearn.feature_extraction.text import TfidfVectorizer
from django.utils import timezone
import collections
import featureextractor
import json
import nltk
import os
import re
import requests
import string
import subprocess
import sys
import texttoconll
import twokenize
import urllib
import urlparse

token_list = {}

def stem_tokens(tokens, stemmer):
	stemmed = []
	for item in tokens:
		stemmed.append(stemmer.stem(item))
	return stemmed

def lemma_tokens(tokens, lmtzr):
	lemmatized = []
	for item in tokens:
		lemmatized.append(lmtzr.lemmatize(item))
	return lemmatized

def tokenize(text):
	stemmer = PorterStemmer()
	# lmtzr = WordNetLemmatizer()
	tokens = twokenize.tokenize(text)
	tokens_clean = [s for s in tokens if s not in set(string.punctuation)]
	# tokens = nltk.word_tokenize(text)
	stems = stem_tokens(tokens_clean, stemmer)
	# lemmas = lemma_tokens(tokens, lmtzr)
	return stems

def extract_txt(url,idx):
	print url
	response = requests.get(url)
	parser = etree.HTMLParser()
	tree = etree.fromstring(response.text, parser)
	all_text = []

	for i in tree.xpath("//div[@class='body']"):
		text = etree.tostring(i, method='text', encoding='UTF-8')
		# lowers = text.lower()
		all_text.append(text)

	return (idx+1, ' '.join(all_text), url)

def log_result(result):
	token_list[result[0]] = result[1]
	o = WebCache.objects.update_or_create(url=result[2], content=result[1])

def crawl(links, token_list):
	p = Pool()
	for idx, record in enumerate(links):
		try:
			web_entry = WebCache.objects.get(url=record)
		except WebCache.DoesNotExist:
			p.apply_async(extract_txt, args=(record,idx), callback = log_result)
			continue
		else:
			if (abs(timezone.now() - web_entry.access_time).days > 30):
				p.apply_async(extract_txt, args=(record,idx), callback = log_result)
				continue
			else:
				token_list[idx+1] = web_entry.content
	p.close()
	p.join()

@csrf_exempt
def extract_entity(request):
	print 'Begin Entity Recognition'
	full_text = request.body
	with open(os.path.join(settings.STATIC_ROOT, 'demo.txt'), 'w') as demo_file:
		demo_file.write(full_text)

	texttoconll.main(os.path.join(settings.STATIC_ROOT, 'demo.txt'), os.path.join(settings.STATIC_ROOT, 'demo.conll'))
	featureextractor.main(os.path.join(settings.STATIC_ROOT, 'demo.conll'), os.path.join(settings.STATIC_ROOT, 'demo.data'))
	p = subprocess.Popen(['crf_test', '-m', os.path.join(settings.STATIC_ROOT, 'CRFmodel0'), os.path.join(settings.STATIC_ROOT, 'demo.data')], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()
	arr = out.splitlines()
	output = []
	
	# write the output to result.txt
	# with open(os.path.join(settings.STATIC_ROOT, 'result.txt'), 'w') as demo_file:
	# 	demo_file.write(out)

	for idx, line in enumerate(arr):
		# if not line.endswith("O") and line:
		if line.endswith("B-API"):
			# remove remaining part after a tab (only entity name)
			temp = re.sub('\t(.+)', ' ', line).strip()
			if (re.search('[a-zA-Z]+', temp)):
				output.append(temp)
	# print output
	return HttpResponse(json.dumps(output))

@csrf_exempt
def link_entity(request):
	print 'Begin Entity Linking'
	body_unicode = request.body.decode('utf-8')
	data = json.loads(body_unicode, object_pairs_hook=OrderedDict)
	data_entity = data["entityList"]
	data_entity_index = data["entityIndex"]
	class_parsed = data["class"]
	class_parsed_index = data["classIndex"]
	class_parsed_list = zip(class_parsed, class_parsed_index)
	question_title = re.findall(r"[\w']+", data["title"].lower())
	tag_list = [x.lower() for x in data["tags"]]
	href_list = [x.lower() for x in data["hrefs"]]
	encode_texts = data["texts"].encode('ascii', errors='xmlcharrefreplace')
	full_text = encode_texts.translate(None, string.punctuation)

	variations = {'np.':'numpy.', 'mpl.':'matplotlib.', 'pd.':'pandas.', 'fig.':'figure.', 'plt.':'pyplot.', 'bxp.':'boxplot.', 'df.':'dataframe.'}
	import_variations = {}
	m = re.findall(r'import (\S+) as (\S+)', encode_texts)
	if (m):
		import_variations = dict((y+'.', x+'.') for x, y in m)
	variations.update(import_variations)

	href_info = [];
	result_list = [];
	class_list = [];
	qualified_entity_list = [];
	
	for href in href_list:
		temp = {}
		o = urlparse.urlsplit(href.encode('ascii','ignore').strip().lower())
		temp['domain'] = o.netloc
		temp['file'] = o.path.rsplit('/', 1)[-1]
		href_info.append(temp)

	for key in data_entity:
		value = data_entity[key]
		try:
			for k, v in variations.iteritems():
				value = re.sub(k, v, value)
			record_list = Record.objects.filter(name=value)
		except Record.DoesNotExist:
			continue
		else:
			if record_list.count() == 0:
				continue
			elif record_list.count() == 1:
				record = record_list[0]
				qualified_entity_list.append(value)
				if record.api_type == "class":
					class_list.append((value, data_entity_index[int(key)]))
			else:
				result_sublist = [];

				# url, tag, title
				for idx, record in enumerate(record_list):
					mark = [False] * 3
					result = {};

					a = record.url.lower()
					r = urlparse.urlsplit(a.encode('ascii','ignore').strip())

					for link in href_info:
						if(link['domain'] == r.netloc and link['file'] == r.path.rsplit('/', 1)[-1]):
							mark[0] = True;

					if record.lib in tag_list:
						mark[1] = True;
					
					if record.lib in question_title:
						mark[2] = True;

					result['score'] = sum(b<<i for i, b in enumerate(mark))
					result['name'] = value
					result['type'] = record.api_class
					result_sublist.append(result)
				maxScoreResult = max(result_sublist, key=lambda x:x['score'])
				if maxScoreResult['type'] == 'class':
					class_list.append((maxScoreResult['name'], data_entity_index[int(key)]))
	class_list = class_list + class_parsed_list
	# print class_list
	qualified_entity_list = set(qualified_entity_list)
	# print qualified_entity_list

	for key in data_entity:
		value = data_entity[key]
		try:
			for k, v in variations.iteritems():
				value = re.sub(k, v, value)
			record_list = Record.objects.filter(name=value)
		except Record.DoesNotExist:
			continue
		else:
			if record_list.count() == 0:
				result = []
				result_list.append(result)
			elif record_list.count() == 1:
				record = record_list[0]
				result = [{}]
				result[0]['name'] = value
				result[0]['type'] = record.api_type
				result[0]['url'] = record.url
				result[0]['lib'] = record.lib
				result_list.append(result)
			else:
				result_sublist = []

				####### tf-idf ##########
				links = []
				tdidf_result = []
				for record in record_list:
					links.append(urlparse.urlsplit(record.url.encode('ascii','ignore').strip()).geturl())

				token_list.clear()
				token_list_sorted = []

				token_list[0] = full_text
				crawl(links, token_list)
				token_od = collections.OrderedDict(sorted(token_list.items()))

				for item in token_od.itervalues():
					token_list_sorted.append(item)

				# gensim
				# dictionary = corpora.Dictionary(token_list)
				# corpus = [dictionary.doc2bow(text) for text in token_list]
				# tfidf = models.TfidfModel(corpus)
				# index = similarities.SparseMatrixSimilarity(tfidf[corpus], num_features=len(dictionary))
				# tdidf_result = index[tfidf[corpus[0]]]

				# sklearn
				tfidf = TfidfVectorizer(tokenizer=tokenize, stop_words='english', ngram_range=(1, 1))
				tfs = tfidf.fit_transform(token_list_sorted)
				tdidf_result = (tfs * tfs.T).A[0]

				######### url, tag, title ############
				for idx, record in enumerate(record_list):
					mark = [False] * 5
					result = {};

					# url
					a = record.url.lower()
					r = urlparse.urlsplit(a.encode('ascii','ignore').strip())

					for link in href_info:
						if(link['domain'] == r.netloc and link['file'] == r.path.rsplit('/', 1)[-1]):
							mark[0] = True;

					# qualified name match
					full_name = record.api_class + '.' + record.name
					for e in qualified_entity_list:
						if (full_name in e):
							mark[1] = True;

					# tag
					if record.lib in tag_list:
						mark[2] = True;
					
					# title
					if record.lib in question_title:
						mark[3] = True;

					# class
					result['distance'] = -1
					for valid_class in class_list:
						# if Levenshtein.ratio(valid_class, record.api_class) > 0.9:
						if valid_class[0] in record.api_class:
							mark[4] = True
							result['distance'] = abs(int(key) - valid_class[1])

					result['mark'] = mark
					result['api_class'] = record.api_class
					result['score'] = sum(b<<i for i, b in enumerate(mark))
					result['name'] = value
					result['url'] = record.url
					result['lib'] = record.lib
					result['type'] = record.api_type
					result['tfidf'] = str(tdidf_result[idx+1])
					result_sublist.append(result)

					minDistanceResult = []
					try:
						minDistanceResult = min((x for x in result_sublist if x['distance'] >= 0), key=lambda x:x['distance'])
					except (ValueError, TypeError):
						pass

					if minDistanceResult:
						for key, result in enumerate(result_sublist):
							if(result['url'] == minDistanceResult['url']):
								result['score'] = result['score'] + 1
				result_list.append(result_sublist)

	# print result_list
	return HttpResponse(json.dumps(result_list))