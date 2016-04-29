// ==UserScript==
// @name         LinkAPI
// @namespace    http://tampermonkey.net/
// @version      0.8
// @description  API linking for S-NER project
// @author       Chee Yong
// @include     http://stackoverflow.com/*
// @include     stackoverflow.com/*
// @include     https://stackoverflow.com/*
// @require     https://raw.githubusercontent.com/padolsey/findAndReplaceDOMText/master/src/findAndReplaceDOMText.js
// @require     https://code.jquery.com/jquery-2.1.4.min.js
// @require     https://code.jquery.com/ui/1.11.4/jquery-ui.min.js
// @resource    jqueryCSS https://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css
// @grant GM_getResourceText
// @grant GM_addStyle
// @grant GM_xmlhttpRequest
// @connect 127.0.0.1
// @connect 128.199.217.19
// ==/UserScript==

var LinkMap = {};
var toBackEndData = {};
var myNodes = [];
var entityList = {};
var linkError = false;
var model_mode = 1; // 0:CRF++, 1:CRFsuite

/* Identify API component */
function identifyAPI() {
    var toNERData = {};
    var fullText = '';

    // extract discussion text, question title and code snippets
    [].forEach.call(
        document.querySelectorAll('.post-text p, #question-header .question-hyperlink, .prettyprinted code'), //include code snippet
        // document.querySelectorAll('.post-text p, #question-header .question-hyperlink'), //exclude code snippet
        function(el) {
            fullText = fullText.concat(' ').concat(el.textContent);
            myNodes.push(el);
        }
    );

    // manual mode (custom list of recognized APIs)
    /*
    var entityJSON = ['eigvals'];
    extractEntity(entityJSON);
    */

    // auto mode (access NER model)
    toNERData['fullText'] = fullText;
    toNERData['mode'] = model_mode;
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/extractentity/", //localhost
        // url: "http://128.199.217.19/entity_recognition/", //external server
        data: JSON.stringify(toNERData),
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: function(response) {
            if (response) {
                try {
                    var entityJSON = JSON.parse(response.responseText);
                    extractEntity(entityJSON);
                } catch(e) {
                    console.log(e);
                    console.log('Something went wrong with entity recognition :-(');
                    return;
                }
            }
        },
        onerror: function(response) {
            console.log("Cannot connect to server! (entity recognition)");

        },
        ontimeout: function(response) {
            console.log("Connection to server timed out! (entity recognition)");
        },
        onabort: function(reponse) {
            console.log("Connection to server aborted! (entity recognition)");
        }
    });
}

/* Extract API in web page */
function extractEntity(entityJSON) {
    var k = 0;
    var entityIndex = [];

    for (var i = entityJSON.length - 1; i >= 0; i--) {
        if (/^[a-zA-Z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]$/.test(entityJSON[i][0]) || entityJSON[i][0] == 'in') {
            entityJSON.splice(i, 1);
        }
    }
    console.log(entityJSON);

    var numOfEntities = entityJSON.length;
    
    // Inject span element to recognized APIs
    myNodes.forEach(function(e, index) {
        var startIdx = 0;
        var remainingText = '';
        var found = 1;
        var retry = 0;
        var ori_e = e.textContent;
        var check_next = 0;

        while (entityJSON[0] && e.textContent.substr(startIdx).indexOf(entityJSON[0][0]) != -1 && found) {
            found = 0;
            findAndReplaceDOMText(e, {
                find: RegExp(entityJSON[0][0]),
                forceContext: function(el) {
                    return el.matches('.api');
                },
                replace: function(portion, match) {
                    check_next = (ori_e.substr(match.endIndex).trim().lastIndexOf(entityJSON[0][1], 0) === 0);
                    remainingText = ori_e.substr(match.endIndex);
                    if(check_next || !remainingText) {
                        var el = document.createElement('span');
                        el.classList.add('api');
                        el.setAttribute("id", numOfEntities-entityJSON.length);
                        el.style.backgroundColor = 'lightgreen';
                        el.innerHTML = portion.text;
                        startIdx = match.endIndex;
                        retry = 0;
                        found = 1;
                        return el;
                    } else if (retry > 3) {
                        var el = document.createElement('non');
                        el.classList.add('api');
                        el.innerHTML = portion.text;
                        startIdx = match.endIndex;
                        retry = 0;
                        found = 1;
                        return el;
                    } else {
                        retry++;
                        return match[0];
                    }
                },
            });

            if(check_next || !remainingText) {
                entityIndex.push(index);
                entityList[k] = entityJSON[0][0];
                k++;
                entityJSON.shift();
            }
        }
    });

    // store list of recognized APIs in term context obj
    toBackEndData["entityList"] = entityList;

    // store list of APIs' positional index in term context obj
    toBackEndData["entityIndex"] = entityIndex;

    // trigger link API component
    entityLinking();

    // customize tooltip styles and options
    $(".api").tooltip({
        items: 'span.api',
        show: false, //show immediately
        track: false,
        content: 'Sorry, entity linking is currently not available',
        html: true,
        tooltipClass: "custom-tooltip-styling",
        position: {
            my: "center top+5",
            at: "bottom"
        },
        open: function(event, ui) {
            ui.tooltip.css("max-width", "600px");
            ui.tooltip.css("max-height", "250px");
            ui.tooltip.css("margin", "auto");
            ui.tooltip.css("font-size", "1em");
            ui.tooltip.css("padding", "5px");
            ui.tooltip.css("background-color", "#f0f0f0");
        },
        close: function(event, ui) {
            ui.tooltip.hover(function() {
                    $(this).stop(true).fadeTo(200, 1);
                },
                function() {
                    $(this).fadeOut('200', function() {
                        $(this).remove();
                    });
                });
        }
    });
}

/* Link API component */
function entityLinking() {
    var tagList = [];
    var allHrefs = [];
    var allTypes = [];
    var typeIndex = [];
    var fullTextSnippet = '';

    // store discussion text, question title and code snippets in term context obj
    [].forEach.call(
        document.querySelectorAll('.post-text p, #question-header .question-hyperlink, .prettyprinted code'),
        function(el) {
            fullTextSnippet = fullTextSnippet.concat(' ').concat(el.textContent);
        }
    );
    toBackEndData["texts"] = fullTextSnippet;

    // store question tags in term context obj
    [].forEach.call(
        document.querySelectorAll('#question .js-gps-track'),
        function(el) {
            tagList.push(el.textContent);
        }
    );
    toBackEndData["tags"] = tagList;

    // store list of hrefs in term context obj
    [].forEach.call(
        document.querySelectorAll('.post-text a,.comment-copy a'),
        function(el) {
            allHrefs.push(el.href);
        }
    );
    toBackEndData["hrefs"] = allHrefs;

    // store question title in term context obj
    var title = document.querySelector('#question-header');
    toBackEndData["title"] = title.textContent;

    // store list of types/classes in term context obj
    myNodes.forEach(function(e, index) {
        [].forEach.call(
            e.querySelectorAll('.typ'),
            function(el) {
                allTypes.push(el.textContent);
                typeIndex.push(index);
            }
        );
    });
    toBackEndData["class"] = allTypes;
    toBackEndData["classIndex"] = typeIndex;

    console.log(toBackEndData);

    // retrieve list of linked code elements
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/linkentity/", //localhost
        // url: "http://128.199.217.19/entity_linking/", //external server
        data: JSON.stringify(toBackEndData),
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: UpdateTooltip,
        onerror: function(response) {
            console.log("Cannot connect to server! (entity linking)");
            linkError = true;
        },
        ontimeout: function(response) {
            console.log("Connection to server timed out! (entity linking)");
            linkError = true;
        },
        onabort: function(reponse) {
            console.log("Connection to server aborted! (entity linking)");
            linkError = true;
        }
    });
}

/* Update tooltip component */
function UpdateTooltip(response) {
    if (response) {
        try {
            linkJSON = JSON.parse(response.responseText);
            console.log(linkJSON);
        } catch(e) {
            console.log(e);
            console.log("Something went wrong with entity linking :-(");
            linkError = true;
            return;
        }
    }

    NumLink = Object.keys(linkJSON).length;

    // dynamically update tooltip content for each recognized API
    for (var i = 0; i < NumLink; i++) {
        if (linkJSON[i].length == 0) {
            contentStr = '<div>No reference found</div>';
            LinkMap[i] = contentStr;
        } else if (linkJSON[i].length == 1) {
            contentStr = "<!DOCTYPE html><html><head><style type='text/css'>";
            contentStr += ".myTable {width:100%;word-wrap:break-word;word-break:break-all;border-spacing:0;border-collapse:collapse;font-size:12px} .myTable th, .myTable td {border: 1px solid black;text-align:center;} .table-scroll {max-width:820px;max-height:250px;overflow-y:scroll;overflow-x:hidden;}</style></head><body>";
            contentStr += "<div class='table-scroll'><table class='myTable'><colgroup><col width='40'><col width='200'><col width='60'><col width='60'></colgroup><tr><th>Rank</th><th>Reference Link</th><th>Type</th><th>Library</th></tr>";
            contentStr += "<tr><td>" + 1 + "</td><td><a href='" + linkJSON[i][0].url + "' target='_blank'>" + linkJSON[i][0].name + "</a></td><td>" + linkJSON[i][0].type + "</td><td>" + linkJSON[i][0].lib + "</td>";
            contentStr += "</table><div></body></html>";
            LinkMap[i] = contentStr;
        } else {
            //sort based on score + tfidf
            linkJSON[i].sort(function(a, b) {
                return (b.score - a.score) + (b.tfidf - a.tfidf);
            });
            contentStr = "<!DOCTYPE html><html><head><style type='text/css'>";
            contentStr += ".myTable {width:100%;word-wrap:break-word;word-break:break-all;border-spacing:0;border-collapse:collapse;font-size:12px;} .myTable th, .myTable td {border: 1px solid black;text-align:center;} .table-scroll {max-width:820px;max-height:250px;overflow-y:scroll;overflow-x:hidden;}</style></head><body>";
            contentStr += "<div class='table-scroll'><table class='myTable'><colgroup><col width='35'><col width='200'><col width='50'><col width='60'><col width='30'><col width='50'><col width='40'><col width='30'><col width='40'><col width='40'></colgroup><tr><th>Rank</th><th>Reference Link</th><th>Type</th><th>Library</th><th>URL</th><th>Match</th><th>Tag</th><th>Title</th><th>Class</th><th>tf-idf</th></tr>";
            for (var j = 0; j < linkJSON[i].length; j++) {
                contentStr += "<tr><td>" + (j + 1) + "</td><td><a href='" + linkJSON[i][j].url + "' target='_blank'>" + ((linkJSON[i][j].api_class != 'none') ? linkJSON[i][j].api_class + "." : "") + linkJSON[i][j].name + "</a></td><td>" + linkJSON[i][j].type + "</td><td>" + linkJSON[i][j].lib + "</td><td>" + ((linkJSON[i][j].mark[0]) ? "&#10004;" : "")  + "</td><td>" + ((linkJSON[i][j].mark[1]) ? "&#10004;" : "") + "</td><td>" + ((linkJSON[i][j].mark[2]) ? "&#10004;" : "") + "</td><td>" + ((linkJSON[i][j].mark[3]) ? "&#10004;" : "") + "</td><td>" + ((linkJSON[i][j].mark[4]) ? "&#10004;" : "") + "</td><td>" + Math.round(linkJSON[i][j].tfidf * 1000) / 1000 + "</td>";
            }
            contentStr += "</table><div></body></html>";
            LinkMap[i] = contentStr;
        }
    }
}

/* Triggering function when a page is loaded */
$(document).ready(function() {
    // incorporate jQuery UI CSS
    var newjqueryCSS = GM_getResourceText("jqueryCSS");
    GM_addStyle(newjqueryCSS);

    // starting of component execution
    identifyAPI();

    // Add mouseover event listener
    $(document.body).on("mouseover", ".api", function() {
        $(this).tooltip("open");
        $(this).tooltip("option", "content", function() {
            var content = LinkMap[this.id];
            if (content)
                return content;
            else if (!linkError)
                return "Loading... (Will ready in few seconds)";
            else
                return "Sorry, entity linking is currently not available";
        });
    });

    // Add mouseleave event listener
    $(document.body).on("mouseleave", ".api", function() {
        $(this).tooltip("close");
    });
});