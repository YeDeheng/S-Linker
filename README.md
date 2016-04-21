# Link API tool
* Recognize CamelCase words in StackOverflow post and link them to their reference documentation respectively.
* All recognized entities will be highlighted in green, and a grey box will show up when you mouse over them.
* If a reference link is found, it will be displayed inside the box.

## Setup guide
### Pre-requisites:
1.  Installed Python 2.7 (with pip)
2.  Installed latest version of [MySQL Community Server](https://dev.mysql.com/downloads/mysql/) and [MySQL Workbench](http://dev.mysql.com/downloads/workbench/)
3.  MySQL service is started
4.  Installed [Tampermonkey](https://chrome.google.com/webstore/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo?hl=en) Chrome extension 

### Instructions:
```
To use `DigitalOcean server`, you can directly jump to last step (Step 7)
```
1. Install Django (version 1.9)
  * `pip install django==1.9`
2. Install MySQL client connector
  * `pip install mysqlclient`
3. Modify MySQL user and password in  `api_linking\django_back\django_back\settings.py` under `DATABASES`
4. Create tables in MySQL
  * Create a database called `link_api` (default collation)
  * Change directory to `django_back`
    * `cd api_linking\django_back`
  * Run following commands:
    * `python manage.py makemigrations`
    * `python manage.py migrate`
  * Import test data into `link_api_record` table in `link_api` database
    * In MySQL workbench, right click the table > select `Table Data Import Wizard` > use `record_data.csv`
5. Create Django admin account (can login via `localhost/admin`)
    * `python manage.py createsuperuser`
6. Run Django server as `localhost`
    * `python manage.py runserver`
7. Add `Link_API.js` javascript in Tampermonkey
    * To use `localhost`, use `url: "http://127.0.0.1:8000/geturl/"` under `GM_xmlhttpRequest` (Make sure localhost has started)
    * To use `DigitalOcean server`, use `url: "http://128.199.217.19/geturl/"` under `GM_xmlhttpRequest`

