import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {dirname} from 'node:path';
export const SEED=20261030;
export const json=x=>JSON.stringify(x,null,2)+'\n';
export const sql=x=>x===null?'NULL':typeof x==='boolean'?(x?'TRUE':'FALSE'):typeof x==='number'?String(x):"'"+x.replaceAll("'","''")+"'";
export const insert=(table,rows)=>'INSERT INTO '+table+' ('+Object.keys(rows[0]).join(',')+') VALUES\n'+rows.map(r=>'('+Object.values(r).map(sql).join(',')+')').join(',\n')+';\n';
export async function artifact(file,content,check){
 if(check){if(await readFile(file,'utf8')!==content)throw Error('Nonreproducible artifact: '+file);}
 else{await mkdir(dirname(file),{recursive:true});await writeFile(file,content);}
}
