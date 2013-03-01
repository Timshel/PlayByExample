'use strict';

var app = angular.module('app', ['ngResource', 'ui', 'ui.bootstrap'])
    .constant("apiUrl", "http://localhost:9000\:9000/api")
    .config(['$routeProvider', function($routeProvider) {
        $routeProvider
            .when('/', {
                templateUrl: '/views/index'
            })
            .when('/search', {
                templateUrl: '/views/search',
                controller: 'SearchCtrl'
            })
            .otherwise({
                redirectTo: '/'
            });
    }])
    .config(['$locationProvider', function($locationProvider) {
        $locationProvider.html5Mode(true).hashPrefix('!');
    }]);

function displayGist(gist) {
    var gistId = gist.div.match(/^<div id="(gist[0-9]+)"/);
    angular.element("#"+gistId[1])
        .append(gist.div)
        .append('<link rel="stylesheet" media="screen" href="'+gist.stylesheet+'">')
}