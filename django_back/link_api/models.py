from __future__ import unicode_literals

from django.db import models

class Record(models.Model):
	id = models.AutoField(primary_key=True)
	name = models.CharField(max_length=200)
	url = models.URLField()
	lib = models.CharField(max_length=200)
	api_type = models.CharField(max_length=200, default='')
	api_class = models.CharField(max_length=200, default='')

	def __str__(self):
		return self.name

class WebCache(models.Model):
	id = models.AutoField(primary_key=True)
	url = models.URLField()
	content = models.TextField()
	access_time = models.DateTimeField(auto_now=True)

	def __str__(self):
		return self.url