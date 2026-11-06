import {reactive} from 'vue';
export const fixture=reactive({side:'plan',state:'NONE',pair:null,fail:false});window.fixture=fixture;
const candidates={plan:[{id:'p1',num:100007,name:'登录回归测试计划',status:'PREPARED'},{id:'p2',num:100008,name:'支付回归测试计划',status:'UNDERWAY'}],review:[{id:'r1',num:100003,name:'登录回归评审',status:'PREPARED'},{id:'r2',num:100004,name:'支付回归评审',status:'UNDERWAY'}]};
export const rows=candidates;
export async function getPlanReview(side,id){if(fixture.fail)throw Error('fixture read failure');const pair=fixture.pair;
if(!pair||pair[side]!==id)return {relationStatus:'NONE',restricted:false,canPair:true};
return {relationStatus:'ACTIVE',restricted:false,relationId:'rel',generation:'g1',target:candidates[side==='plan'?'review':'plan'].find(x=>x.id===pair[side==='plan'?'review':'plan']),sync:{state:fixture.state,sourceVersion:18,appliedVersion:17,blockers:[],attempts:2},canUnlink:true,canReconfirm:['FAILED_MANUAL','RECONFIRM_REQUIRED'].includes(fixture.state)};}
export async function getPlanReviewCandidates({side,keyword,page}){const items=candidates[side==='plan'?'review':'plan'].filter(x=>(x.name+x.num).includes(keyword));return {items,total:items.length};}
let preview;
export async function previewPlanReview(value){preview=value;return {previewId:'preview',generation:'g1',sourceVersion:18,expiresAt:Date.now()+600000,message:'以测试计划为准，不修改测试计划；移出成员保留历史，重新加入须重新评审。',difference:{added:['a'],removed:['c'],retained:['b'],reentered:[]}};}
export async function confirmPlanReview(){fixture.pair={plan:preview.planId,review:preview.reviewId};fixture.state='PENDING';return {};}
export async function unlinkPlanReview(){fixture.pair=null;fixture.state='NONE';}
export const getPlanReviewPreviewItems=async()=>({items:[{id:'a',num:100121,name:'登录验证用例'}]});
export const getPlanReviewReadOnlyDifference=async()=>({readonly:true,generation:'g1',items:[{id:'a',num:100121,name:'登录验证用例'}],total:1});
export const getPlanReviewOperations=async()=>({items:[]});
export const getPlanReviewHistory=async()=>({items:[],total:0});
export const getPlanReviewFrozen=async()=>({available:false});
export const getPlanReviewGrant=async()=>({revision:1,enabled:true});
export const setPlanReviewGrant=async()=>({});
export const previewPlanReviewLifecycle=async()=>({});
export const confirmPlanReviewLifecycle=async()=>({});
export const resumePlanReviewOperation=async()=>({});
export const terminatePlanReviewOperation=async()=>({});

export const findPlanReviewConfirmation=async()=>({items:[]});
