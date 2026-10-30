import SwaggerParser from '@apidevtools/swagger-parser';
import {readFile} from 'node:fs/promises';
const document=JSON.parse(await readFile(new URL('../openapi.json',import.meta.url),'utf8'));
// Only local refs; no remote resolution or network is needed.
await SwaggerParser.validate(document,{resolve:{external:false},dereference:{circular:false}});
console.log('PASS: pinned Swagger Parser validates OpenAPI 3.0.3; external resolution disabled.');
