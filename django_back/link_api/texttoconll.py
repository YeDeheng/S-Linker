
# -*- coding: utf-8 -*-
import sys
import re
import os
from os.path import basename
from cStringIO import StringIO

sys.path.append(os.path.join(os.path.dirname(__file__), 'mylib'))
sys.path.append('.')
from sentencesplit import sentencebreaks_to_newlines
from mytokenizer import mytokenizer

def regex_or(*items):
  r = '|'.join(items)
  r = '(' + r + ')'
  return r

#  Oct23: overcome cases like i.e
API_pattern = re.compile(
    regex_or(r'^(?:[a-zA-Z_][a-zA-Z_]+\.)+[a-zA-Z_][a-zA-Z_]+\(\)$',
    r'^[a-zA-Z\.\_][a-zA-Z\.\_]+\(\)$',
    r'^(?:[a-zA-Z_][a-zA-Z_]+\.)+[a-zA-Z_][a-zA-Z_]+$',
    r'^(?:[A-Za-z]+)+[A-Z][a-z]+$' )
    )

# TOKENIZATION_REGEX = re.compile(API)
NEWLINE_TERM_REGEX = re.compile(r'(.*?\n)')

api_list = []

def text_to_conll(f):
    """Convert plain text into CoNLL format."""
    sentences = []
    for l in f:
        l = sentencebreaks_to_newlines(l)
        sentences.extend([s for s in NEWLINE_TERM_REGEX.split(l) if s])

    lines = []
    for s in sentences:
        nonspace_token_seen = False
        tokens = [t for t in s.split() if t]
        for t in tokens:
            if not t.isspace():
                # pre label rules designed by Deheng
                #if API_pattern.match(t) is not None:
                #    lines.append([t, 'B-API'])
                if t.endswith("()"):
                    #print t
                    t_nobracket = t[:-2]
                    if t_nobracket in api_list:
                        lines.append([t, 'B-API'])
                elif t in api_list:
                    #print t
                    lines.append([t, 'B-API'])
                else:
                    lines.append([t, 'O'])
                nonspace_token_seen = True
        # sentences delimited by empty lines
        if nonspace_token_seen:
            lines.append([])

    lines = [[l[0], l[1]] if l else l for l in lines]
    return StringIO('\n'.join(('\t'.join(l) for l in lines)))

def build_list():
    f = open('./apidoc/all-remove.txt', 'r')
    for line in f:
        api = line.strip()
        api_list.append(api)
    return api_list

def main(arg1, arg2):
    #api_list = build_list()
    api_list = []

    if arg1.endswith('.txt'):
        filebase = '.'.join(arg1.split('.')[:-1]) if '.' in arg1 else arg1
    tokenfile = str(filebase) + '.tk'

    mytokenizer.tokenize(arg1, tokenfile)
    f = open(tokenfile, 'r')
    lines = text_to_conll(f)
    with open(arg2, 'wt') as of:
        of.write(''.join(lines))

if __name__ == '__main__':
    main(*sys.argv[1:])

