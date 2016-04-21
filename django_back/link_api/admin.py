from django.contrib import admin

from .models import Record

class RecordAdmin(admin.ModelAdmin):
	list_display = ['name','url']

admin.site.register(Record, RecordAdmin)