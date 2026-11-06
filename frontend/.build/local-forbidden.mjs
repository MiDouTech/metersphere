
import ts from 'typescript';import fs from 'node:fs';
const file='src/api/modules/plan-review/index.ts';let text=fs.readFileSync(file,'utf8');
const source=ts.createSourceFile(file,text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS);const points=[];
function visit(node){if(ts.isCallExpression(node)&&ts.isPropertyAccessExpression(node.expression)&&node.expression.expression.getText(source)==='MSR'&&node.arguments.length===1)points.push(node.end-1);ts.forEachChild(node,visit);}visit(source);
for(const point of points.sort((a,b)=>b-a))text=text.slice(0,point)+', relationRequestOptions'+text.slice(point);
text=text.replace("import MSR from '@/api/http/index';", "import MSR from '@/api/http/index';\n\n// A denied relation operation stays in its current dialog; authentication and server authorization remain unchanged.\nconst relationRequestOptions = { handleForbiddenLocally: true };");
fs.writeFileSync(file,text,'utf8');console.log('Scoped forbidden handling:',points.length,'requests');
