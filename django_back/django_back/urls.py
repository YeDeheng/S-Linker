from django.conf.urls import patterns, include, url

from django.contrib import admin
admin.autodiscover()

from link_api import views

urlpatterns = [
    # url(r'^$', 'django_project.views.home', name='home'),
	url(r'^entity_linking/$', views.link_entity),
    url(r'^entity_recognition/$', views.extract_entity),
    url(r'^admin/', admin.site.urls),
]
