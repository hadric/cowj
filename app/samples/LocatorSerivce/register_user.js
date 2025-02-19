// data stores
redis = _ds.get('redis')
jdbc = _ds.get('pgsql')

isRedisActive = true
try {
redis.ping()
}
catch(err) {
isRedisActive = false
}


// request body as JSON
input = req.attribute("_body");
latitude = input.get('latitude')
longitude = input.get('longitude')
personId = input.get('personId')

//business logic
invalidLocation = parseInt(latitude) > 180 || parseInt(latitude) < -180
 || parseInt(longitude) > 180 || parseInt(longitude) < -180

Test.expect(!invalidLocation, "invalid location coordinates", 422 )

lastSeen = Date.now()
if(isRedisActive) {
redis.set(personId, JSON.stringify( { latitude : latitude, longitude : longitude, last_seen : lastSeen}))
}

// insert or update the data to mysql table. This can be moved to a async processor to further speed up the API
findPerson = `select * from locationService.latestLocation where person_id = "${personId}";`;
data = jdbc.select(findPerson, []);
result = data.value();

res = ''
result.forEach( m => {res += m.get("person_id");});

con = jdbc.connection().value();
if(res == '') {
// insert a new entry
insertSql = `insert into latestLocation VALUES( "${personId}" ,'{"latitude": "${latitude}", "longitude":  "${longitude}", "last_seen":  "${lastSeen}"}');`
stmt = con.createStatement()
data = stmt.execute(insertSql)
} else {
// update existing entry
updateSql = `UPDATE latestLocation
 set data = '{"latitude": "${latitude}", "longitude":  "${longitude}", "last_seen":  "${lastSeen}"}'
 where person_id = "${personId}" ;`
 stmt = con.createStatement()
 data = stmt.execute(updateSql)
}

registerLocation = `insert into locations VALUES( "${personId}" ,'{"latitude": "${latitude}", "longitude":  "${longitude}", "last_seen":  "${lastSeen}"}', now());`

try {
stmt = con.createStatement()
data = stmt.execute(registerLocation)
}
catch(err) {
Test.panic(err.toString().startsWith('JavaException: java.sql.SQLIntegrityConstraintViolationException'), 'duplicate request', 409 )
throw err;
}

JSON.stringify( { last_seen : lastSeen})